package br.com.castel.identity.application;

import br.com.castel.identity.api.AuthenticatedUser;
import br.com.castel.identity.api.UserId;
import br.com.castel.identity.application.TooManyLoginAttemptsException.Reason;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Orchestrates login, refresh, access token authentication, logout and "who am I".
 *
 * <p>Holds no authentication rule itself. Username format, password match, user status and session
 * currency are decided by {@link User}, token validity by {@link TokenIssuer}, and throttling by
 * {@link LoginAttemptRateLimiter}. This class only branches on those decisions to pick the audit
 * log line and the exception.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    /** Same access token lifetime the {@link TokenIssuer} issues for. */
    private static final long ACCESS_TOKEN_EXPIRES_IN_SECONDS = Duration.ofMinutes(15).toSeconds();

    private static final int DUMMY_PASSWORD_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final LoginAttemptRateLimiter rateLimiter;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    /**
     * Hash of a random password nobody knows, made by {@link #passwordEncoder} itself so it costs
     * exactly what a stored hash costs. Compared against when the user does not exist, to keep the
     * response time of "user not found" close to "wrong password".
     */
    private final String dummyPasswordHash;

    /**
     * Random password nobody knows, compared in place of a null or empty one. Spring Security's
     * encoders return {@code false} for an empty raw password without hashing anything, which would
     * leave only the database time and tell existing accounts apart.
     */
    private final String substituteForEmptyPassword;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenIssuer tokenIssuer,
            LoginAttemptRateLimiter rateLimiter,
            TransactionOperations transactionOperations,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.rateLimiter = rateLimiter;
        this.transactionOperations = transactionOperations;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(randomPassword());
        this.substituteForEmptyPassword = randomPassword();
    }

    /**
     * Authenticates by username (case-insensitive) and password, and issues a fresh token pair on
     * success.
     *
     * <p>Rejects an unknown user, a username that is not well formed, a wrong password and an
     * inactive user with the exact same exception. Exactly one password comparison runs before any
     * decision is taken, whatever the case, so the timing does not tell them apart either. A
     * username that is not well formed can belong to no user: it is never looked up, and it is
     * compared against the dummy hash like an unknown user.
     *
     * <p>Throttling: a pair (IP, username) or an IP already at its failure limit is rejected
     * before any password comparison. Otherwise the attempt runs inside one of the IP's concurrent
     * password check slots, waiting for one if needed; the limit is checked again once the slot is
     * held. A failure is counted after the comparison, still inside the slot. Being rejected by the
     * slots (waiting limit, timeout, interruption) is not a failure.
     *
     * <p>No lock and no connection is held during the comparison. The user is read without a lock
     * in a short transaction that ends before the comparison, so concurrent attempts on the same
     * user run in parallel exactly like attempts on an unknown user. Only a matching password of an
     * active user opens a second short transaction, which locks the row, bumps the session and
     * issues the tokens. If by then the user is gone or inactive, the login fails like any other.
     * The password is not compared again under the lock: a password changed in those milliseconds
     * is accepted once more (decision #61).
     *
     * <p>Every rejection is written to the audit log with the client IP and the username as tried,
     * quoted and escaped by {@link AuditLogText}; never the password.
     *
     * @throws TooManyLoginAttemptsException if the pair or the IP is at its failure limit, even
     *         when the credentials are correct, or if the IP's password check slots reject the
     *         attempt
     * @throws InvalidCredentialsException if the username, password or user status is wrong
     */
    public AuthenticationResult login(String rawUsername, String rawPassword, String clientIp) {
        String username = User.normalizedUsername(rawUsername);
        // An empty password would skip the hash entirely (see substituteForEmptyPassword): compare a
        // random one instead, so every path still spends one full BCrypt and fails as usual.
        String password = rawPassword == null || rawPassword.isEmpty() ? substituteForEmptyPassword : rawPassword;
        LoginAttempt attempt = new LoginAttempt(username, password, clientIp, AuditLogText.quote(rawUsername));

        try {
            requireNotBlocked(attempt);
            return rateLimiter.runWithPasswordCheckSlot(clientIp, () -> {
                        requireNotBlocked(attempt);
                        return authenticate(attempt);
                    })
                    .orElseThrow(InvalidCredentialsException::new);
        } catch (TooManyLoginAttemptsException throttled) {
            log.warn("login rejected: {} (username={}, ip={})", throttled.reason(), attempt.auditUsername(), clientIp);
            throw throttled;
        }
    }

    /**
     * Exchanges a valid refresh token of a still current session for a new access token, stamped
     * with the roles currently stored on the user.
     *
     * @throws InvalidTokenException if {@code refreshToken} is malformed, badly signed, or not a
     *         refresh token
     * @throws TokenExpiredException if {@code refreshToken} is expired
     * @throws UserInactiveException if the user was deactivated
     * @throws SessionSupersededException if the user no longer exists, or its session moved on
     *         since the token was issued
     */
    @Transactional(readOnly = true)
    public RefreshedAccessToken refresh(String refreshToken) {
        TokenClaims claims = tokenIssuer.parseAndValidate(refreshToken, TokenType.REFRESH);
        User user = findUserOfCurrentSession(claims);
        String accessToken = tokenIssuer.issueAccessToken(user);
        return new RefreshedAccessToken(accessToken, ACCESS_TOKEN_EXPIRES_IN_SECONDS);
    }

    /**
     * Resolves the user behind a bearer access token, for the authentication filter.
     *
     * @throws InvalidTokenException if {@code accessToken} is malformed, badly signed, or not an
     *         access token
     * @throws TokenExpiredException if {@code accessToken} is expired
     * @throws UserInactiveException if the user was deactivated
     * @throws SessionSupersededException if the user no longer exists, or its session moved on
     *         since the token was issued
     */
    public AuthenticatedUser authenticateAccessToken(String accessToken) {
        TokenClaims claims = tokenIssuer.parseAndValidate(accessToken, TokenType.ACCESS);
        User user = findUserOfCurrentSession(claims);
        return new AuthenticatedUser(user.id(), user.username(), user.roles());
    }

    /**
     * Revokes every session of {@code userId}, invalidating every token issued so far.
     *
     * <p>Reads the row under a write lock, so a concurrent login or logout for the same user cannot
     * lose the {@code tokenVersion} bump (see {@link UserRepository#findByIdForUpdate(UserId)}).
     *
     * @throws SessionSupersededException if the user no longer exists
     */
    @Transactional
    public void logout(UserId userId) {
        User lockedUser = userRepository.findByIdForUpdate(userId).orElseThrow(SessionSupersededException::new);
        lockedUser.revokeSessions();
        userRepository.save(lockedUser);
    }

    /**
     * Loads the authenticated user; reading "who am I" changes nothing.
     *
     * @throws SessionSupersededException if the user no longer exists
     */
    @Transactional(readOnly = true)
    public User me(UserId userId) {
        return userRepository.findById(userId).orElseThrow(SessionSupersededException::new);
    }

    private void requireNotBlocked(LoginAttempt attempt) {
        if (rateLimiter.isBlocked(attempt.clientIp(), attempt.username())) {
            throw new TooManyLoginAttemptsException(Reason.FAILURE_LIMIT_REACHED);
        }
    }

    /**
     * Runs inside the password check slot, outside any transaction; empty means the login failed.
     * The password comparison, real or dummy, always runs before the first decision.
     */
    private Optional<AuthenticationResult> authenticate(LoginAttempt attempt) {
        boolean wellFormed = User.isWellFormedUsername(attempt.username());
        Optional<User> found = wellFormed ? readUserWithoutLock(attempt.username()) : Optional.empty();
        boolean passwordMatches = found
                .map(user -> user.matches(attempt.password(), passwordEncoder))
                .orElseGet(() -> spendDummyComparison(attempt.password()));

        if (!wellFormed) {
            return rejectLogin("malformed username", attempt);
        }
        if (found.isEmpty()) {
            return rejectLogin("user not found", attempt);
        }
        if (!passwordMatches) {
            return rejectLogin("wrong password", attempt);
        }
        if (!found.get().canAuthenticate()) {
            return rejectLogin("inactive user", attempt);
        }
        Optional<AuthenticationResult> registered = registerLoginUnderLock(found.get().id());
        if (registered.isEmpty()) {
            return rejectLogin("user removed or deactivated during login", attempt);
        }
        return registered;
    }

    /** Short transaction that ends, and gives its connection back, before the password comparison. */
    private Optional<User> readUserWithoutLock(String username) {
        return transactionOperations.execute(status -> userRepository.findByUsername(username));
    }

    /**
     * Short transaction that locks the row, bumps the session and issues the tokens; empty if the
     * user no longer exists or can no longer authenticate.
     */
    private Optional<AuthenticationResult> registerLoginUnderLock(UserId userId) {
        return transactionOperations.execute(status -> userRepository.findByIdForUpdate(userId)
                .filter(User::canAuthenticate)
                .map(lockedUser -> {
                    lockedUser.registerLogin(clock);
                    userRepository.save(lockedUser);
                    return new AuthenticationResult(lockedUser, tokenIssuer.issueTokenPair(lockedUser));
                }));
    }

    /**
     * User status is checked before session currency: {@link User#deactivate()} also bumps
     * {@code tokenVersion}, so the reverse order would report a deactivated user's token as
     * superseded.
     */
    private User findUserOfCurrentSession(TokenClaims claims) {
        User user = userRepository.findById(claims.userId()).orElseThrow(SessionSupersededException::new);
        if (!user.canAuthenticate()) {
            throw new UserInactiveException();
        }
        if (!user.isSessionCurrent(claims.tokenVersion())) {
            throw new SessionSupersededException();
        }
        return user;
    }

    private boolean spendDummyComparison(String candidatePassword) {
        passwordEncoder.matches(candidatePassword, dummyPasswordHash);
        return false;
    }

    private Optional<AuthenticationResult> rejectLogin(String reason, LoginAttempt attempt) {
        rateLimiter.recordFailure(attempt.clientIp(), attempt.username());
        log.warn("login failed: {} (username={}, ip={})", reason, attempt.auditUsername(), attempt.clientIp());
        return Optional.empty();
    }

    private static String randomPassword() {
        byte[] randomBytes = new byte[DUMMY_PASSWORD_BYTES];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes);
    }

    /**
     * One login attempt: the normalized username used for the lookup and the rate limit, and the
     * username as tried, already made safe for the audit log. {@code toString} never shows the
     * password.
     */
    private record LoginAttempt(String username, String password, String clientIp, String auditUsername) {

        @Override
        public String toString() {
            return "LoginAttempt{username=%s, clientIp=%s}".formatted(auditUsername, clientIp);
        }
    }
}
