package br.com.castel.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

import br.com.castel.identity.api.AuthenticatedUser;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import br.com.castel.identity.infra.InMemoryLoginAttemptRateLimiter;
import br.com.castel.identity.infra.JwtTokenIssuer;
import br.com.castel.identity.infra.LoginRateLimitProperties;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Written from the security acceptance rules of docs/task-0.4-identity-auth.md.
 *
 * <p>Only the persistence port ({@link UserRepository}) is mocked. The password encoder is a spy
 * over a real low-cost BCrypt, so calls can be verified; the token issuer, the rate limiter and
 * the {@link User} aggregate are real, driven by a controllable clock.
 */
class AuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private static final String SECRET = "test-only-jwt-secret-with-more-than-256-bits-0123456789abcdef";
    private static final String USERNAME = "joao.silva";
    private static final String PASSWORD = "Leak-Canary-Password-42";
    private static final String WRONG_PASSWORD = "Wrong-Canary-Guess-77";
    private static final String CLIENT_IP = "198.51.100.7";

    private MutableClock clock;
    private PasswordEncoder encoder;
    private UserRepository repository;
    private TokenIssuer tokenIssuer;
    private InMemoryLoginAttemptRateLimiter limiter;
    private AuthenticationService service;
    private User user;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        encoder = spy(new BCryptPasswordEncoder(4));
        repository = mock(UserRepository.class);
        tokenIssuer = new JwtTokenIssuer(SECRET, clock);
        LoginRateLimitProperties limitProperties = new LoginRateLimitProperties(
                10, 100, Duration.ofMinutes(1), 10_000, 10_000, 3, 30, Duration.ofSeconds(5));
        limiter = new InMemoryLoginAttemptRateLimiter(
                InMemoryLoginAttemptRateLimiter.newFailuresCache(10_000, limitProperties.window(), clock),
                InMemoryLoginAttemptRateLimiter.newFailuresCache(10_000, limitProperties.window(), clock),
                limitProperties,
                clock);
        service = new AuthenticationService(
                repository, encoder, tokenIssuer, limiter, TransactionOperations.withoutTransaction(), clock);
        user = User.create(UUID.randomUUID(), USERNAME, "Joao da Silva", PASSWORD, Set.of(Role.WAITER), encoder);
        when(repository.save(any(User.class))).then(returnsFirstArg());
        clearInvocations(encoder);
    }

    private void givenUserIsStored() {
        when(repository.findByUsername(anyString())).thenReturn(Optional.of(user));
        when(repository.findById(user.id())).thenReturn(Optional.of(user));
        when(repository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
    }

    private void givenNoUserIsStored() {
        when(repository.findByUsername(anyString())).thenReturn(Optional.empty());
    }

    private void givenStoredUserWasDeleted() {
        when(repository.findById(user.id())).thenReturn(Optional.empty());
        when(repository.findByIdForUpdate(user.id())).thenReturn(Optional.empty());
    }

    private void givenUserIsStoredAndInactive() {
        user.deactivate();
        givenUserIsStored();
    }

    private void failLogins(String password, int count) {
        IntStream.range(0, count).forEach(attempt -> catchThrowable(() -> service.login(USERNAME, password, CLIENT_IP)));
    }

    @Nested
    class IndistinguishableCredentialFailures {

        @Test
        void shouldRejectUnknownUserWithInvalidCredentials() {
            givenNoUserIsStored();

            assertThatThrownBy(() -> service.login(USERNAME, PASSWORD, CLIENT_IP))
                    .asInstanceOf(type(InvalidCredentialsException.class))
                    .extracting(InvalidCredentialsException::code)
                    .isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        void shouldRejectWrongPasswordWithInvalidCredentials() {
            givenUserIsStored();

            assertThatThrownBy(() -> service.login(USERNAME, WRONG_PASSWORD, CLIENT_IP))
                    .asInstanceOf(type(InvalidCredentialsException.class))
                    .extracting(InvalidCredentialsException::code)
                    .isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        void shouldRejectInactiveUserWithCorrectPasswordWithInvalidCredentials() {
            givenUserIsStoredAndInactive();

            assertThatThrownBy(() -> service.login(USERNAME, PASSWORD, CLIENT_IP))
                    .asInstanceOf(type(InvalidCredentialsException.class))
                    .extracting(InvalidCredentialsException::code)
                    .isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        void shouldRaiseSameExceptionCodeAndMessageForUnknownUserWrongPasswordAndInactiveUser() {
            givenNoUserIsStored();
            Throwable unknownUser = catchThrowable(() -> service.login("ghost.user", PASSWORD, CLIENT_IP));
            givenUserIsStored();
            Throwable wrongPassword = catchThrowable(() -> service.login(USERNAME, WRONG_PASSWORD, CLIENT_IP));
            user.deactivate();
            Throwable inactiveUser = catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            assertThat(wrongPassword).hasSameClassAs(unknownUser);
            assertThat(inactiveUser).hasSameClassAs(unknownUser);
            assertThat(((InvalidCredentialsException) wrongPassword).code())
                    .isEqualTo(((InvalidCredentialsException) unknownUser).code());
            assertThat(((InvalidCredentialsException) inactiveUser).code())
                    .isEqualTo(((InvalidCredentialsException) unknownUser).code());
            assertThat(wrongPassword).hasMessage(unknownUser.getMessage());
            assertThat(inactiveUser).hasMessage(unknownUser.getMessage());
        }

        @Test
        void shouldRunPasswordComparisonEvenWhenUserDoesNotExist() {
            givenNoUserIsStored();

            catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            verify(encoder, atLeastOnce()).matches(any(), any());
        }

        @Test
        void shouldRunPasswordComparisonWhenUserIsInactive() {
            givenUserIsStoredAndInactive();

            catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            verify(encoder, atLeastOnce()).matches(any(), any());
        }

        @Test
        void shouldNotRegisterLoginWhenPasswordIsWrong() {
            givenUserIsStored();
            int versionBefore = user.tokenVersion();

            catchThrowable(() -> service.login(USERNAME, WRONG_PASSWORD, CLIENT_IP));

            assertThat(user.tokenVersion()).isEqualTo(versionBefore);
            assertThat(user.lastLoginAt()).isNull();
        }

        @Test
        void shouldNotRegisterLoginWhenUserIsInactive() {
            givenUserIsStoredAndInactive();
            int versionBefore = user.tokenVersion();

            catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            assertThat(user.tokenVersion()).isEqualTo(versionBefore);
            assertThat(user.lastLoginAt()).isNull();
        }
    }

    @Nested
    class SuccessfulLogin {

        @Test
        void shouldRegisterLoginWithInjectedClock() {
            givenUserIsStored();
            int versionBefore = user.tokenVersion();

            service.login(USERNAME, PASSWORD, CLIENT_IP);

            assertThat(user.tokenVersion()).isEqualTo(versionBefore + 1);
            assertThat(user.lastLoginAt()).isEqualTo(NOW);
        }

        @Test
        void shouldIssueAccessTokenCarryingTokenVersionAfterLogin() {
            givenUserIsStored();

            AuthenticationResult result = service.login(USERNAME, PASSWORD, CLIENT_IP);

            TokenClaims claims = tokenIssuer.parseAndValidate(result.tokens().accessToken(), TokenType.ACCESS);
            assertThat(claims.tokenVersion()).isEqualTo(user.tokenVersion());
        }

        @Test
        void shouldIssueRefreshTokenCarryingTokenVersionAfterLogin() {
            givenUserIsStored();

            AuthenticationResult result = service.login(USERNAME, PASSWORD, CLIENT_IP);

            TokenClaims claims = tokenIssuer.parseAndValidate(result.tokens().refreshToken(), TokenType.REFRESH);
            assertThat(claims.tokenVersion()).isEqualTo(user.tokenVersion());
        }

        @Test
        void shouldAuthenticateAccessTokenOfLatestLogin() {
            givenUserIsStored();
            AuthenticationResult result = service.login(USERNAME, PASSWORD, CLIENT_IP);

            AuthenticatedUser authenticated = service.authenticateAccessToken(result.tokens().accessToken());

            assertThat(authenticated.id()).isEqualTo(user.id());
            assertThat(authenticated.roles()).containsExactly(Role.WAITER);
        }
    }

    @Nested
    class SingleSession {

        @Test
        void shouldSupersedeAccessTokenAfterNewLogin() {
            givenUserIsStored();
            String firstAccessToken = service.login(USERNAME, PASSWORD, CLIENT_IP).tokens().accessToken();
            service.login(USERNAME, PASSWORD, CLIENT_IP);

            assertThatThrownBy(() -> service.authenticateAccessToken(firstAccessToken))
                    .asInstanceOf(type(SessionSupersededException.class))
                    .extracting(SessionSupersededException::code)
                    .isEqualTo("SESSION_SUPERSEDED");
        }

        @Test
        void shouldSupersedeRefreshTokenAfterNewLogin() {
            givenUserIsStored();
            String firstRefreshToken = service.login(USERNAME, PASSWORD, CLIENT_IP).tokens().refreshToken();
            service.login(USERNAME, PASSWORD, CLIENT_IP);

            assertThatThrownBy(() -> service.refresh(firstRefreshToken)).isInstanceOf(SessionSupersededException.class);
        }

        @Test
        void shouldSupersedeAccessTokenAfterLogout() {
            givenUserIsStored();
            String accessToken = service.login(USERNAME, PASSWORD, CLIENT_IP).tokens().accessToken();
            service.logout(user.id());

            assertThatThrownBy(() -> service.authenticateAccessToken(accessToken))
                    .isInstanceOf(SessionSupersededException.class);
        }

        @Test
        void shouldSupersedeRefreshTokenAfterLogout() {
            givenUserIsStored();
            String refreshToken = service.login(USERNAME, PASSWORD, CLIENT_IP).tokens().refreshToken();
            service.logout(user.id());

            assertThatThrownBy(() -> service.refresh(refreshToken)).isInstanceOf(SessionSupersededException.class);
        }

        @Test
        void shouldRejectAccessTokenOfDeactivatedUserWithUserInactive() {
            givenUserIsStored();
            String accessToken = service.login(USERNAME, PASSWORD, CLIENT_IP).tokens().accessToken();
            user.deactivate();

            assertThatThrownBy(() -> service.authenticateAccessToken(accessToken))
                    .asInstanceOf(type(UserInactiveException.class))
                    .extracting(UserInactiveException::code)
                    .isEqualTo("USER_INACTIVE");
        }

        @Test
        void shouldRejectRefreshOfDeactivatedUserWithUserInactive() {
            givenUserIsStored();
            String refreshToken = service.login(USERNAME, PASSWORD, CLIENT_IP).tokens().refreshToken();
            user.deactivate();

            assertThatThrownBy(() -> service.refresh(refreshToken))
                    .asInstanceOf(type(UserInactiveException.class))
                    .extracting(UserInactiveException::code)
                    .isEqualTo("USER_INACTIVE");
        }

        @Test
        void shouldRejectAccessTokenOfUserThatNoLongerExistsWithSessionSuperseded() {
            givenUserIsStored();
            String accessToken = service.login(USERNAME, PASSWORD, CLIENT_IP).tokens().accessToken();
            givenStoredUserWasDeleted();

            assertThatThrownBy(() -> service.authenticateAccessToken(accessToken))
                    .asInstanceOf(type(SessionSupersededException.class))
                    .extracting(SessionSupersededException::code)
                    .isEqualTo("SESSION_SUPERSEDED");
        }

        @Test
        void shouldRejectRefreshTokenOfUserThatNoLongerExistsWithSessionSuperseded() {
            givenUserIsStored();
            String refreshToken = service.login(USERNAME, PASSWORD, CLIENT_IP).tokens().refreshToken();
            givenStoredUserWasDeleted();

            assertThatThrownBy(() -> service.refresh(refreshToken)).isInstanceOf(SessionSupersededException.class);
        }
    }

    @Nested
    class TokenUsage {

        @Test
        void shouldRejectRefreshTokenUsedAsAccessToken() {
            givenUserIsStored();
            String refreshToken = service.login(USERNAME, PASSWORD, CLIENT_IP).tokens().refreshToken();

            assertThatThrownBy(() -> service.authenticateAccessToken(refreshToken))
                    .asInstanceOf(type(InvalidTokenException.class))
                    .extracting(InvalidTokenException::code)
                    .isEqualTo("INVALID_TOKEN");
        }

        @Test
        void shouldRejectAccessTokenUsedForRefresh() {
            givenUserIsStored();
            String accessToken = service.login(USERNAME, PASSWORD, CLIENT_IP).tokens().accessToken();

            assertThatThrownBy(() -> service.refresh(accessToken)).isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void shouldRejectExpiredAccessTokenWithTokenExpired() {
            givenUserIsStored();
            String accessToken = service.login(USERNAME, PASSWORD, CLIENT_IP).tokens().accessToken();
            clock.advance(Duration.ofMinutes(15).plusSeconds(1));

            assertThatThrownBy(() -> service.authenticateAccessToken(accessToken))
                    .isInstanceOf(TokenExpiredException.class);
        }

        @Test
        void shouldIssueUsableAccessTokenOnRefreshAfterAccessTokenExpired() {
            givenUserIsStored();
            String refreshToken = service.login(USERNAME, PASSWORD, CLIENT_IP).tokens().refreshToken();
            clock.advance(Duration.ofMinutes(16));

            RefreshedAccessToken refreshed = service.refresh(refreshToken);

            assertThat(service.authenticateAccessToken(refreshed.accessToken()).id()).isEqualTo(user.id());
        }

        @Test
        void shouldNotChangeTokenVersionOnRefresh() {
            givenUserIsStored();
            String refreshToken = service.login(USERNAME, PASSWORD, CLIENT_IP).tokens().refreshToken();
            int versionAfterLogin = user.tokenVersion();

            service.refresh(refreshToken);

            assertThat(user.tokenVersion()).isEqualTo(versionAfterLogin);
        }
    }

    @Nested
    class LoginRateLimit {

        @Test
        void shouldCountWrongPasswordAsFailure() {
            givenUserIsStored();

            failLogins(WRONG_PASSWORD, 10);

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isTrue();
        }

        @Test
        void shouldCountUnknownUserAsFailure() {
            givenNoUserIsStored();

            failLogins(PASSWORD, 10);

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isTrue();
        }

        @Test
        void shouldCountInactiveUserAsFailure() {
            givenUserIsStoredAndInactive();

            failLogins(PASSWORD, 10);

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isTrue();
        }

        @Test
        void shouldNotCountSuccessfulLoginsAsFailures() {
            givenUserIsStored();

            IntStream.range(0, 10).forEach(attempt -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isFalse();
        }

        @Test
        void shouldAllowCorrectLoginAfterNineFailures() {
            givenUserIsStored();
            failLogins(WRONG_PASSWORD, 9);

            AuthenticationResult result = service.login(USERNAME, PASSWORD, CLIENT_IP);

            assertThat(result.tokens().accessToken()).isNotBlank();
        }

        @Test
        void shouldRejectBlockedPairEvenWithCorrectPassword() {
            givenUserIsStored();
            IntStream.range(0, 10).forEach(attempt -> limiter.recordFailure(CLIENT_IP, USERNAME));

            assertThatThrownBy(() -> service.login(USERNAME, PASSWORD, CLIENT_IP))
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::code)
                    .isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        }

        @Test
        void shouldRejectEleventhLoginWithCorrectPasswordAfterTenFailedLogins() {
            givenUserIsStored();
            failLogins(WRONG_PASSWORD, 10);

            assertThatThrownBy(() -> service.login(USERNAME, PASSWORD, CLIENT_IP))
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::code)
                    .isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        }

        @Test
        void shouldRejectEleventhLoginWithWrongPasswordWithTooManyAttemptsInsteadOfInvalidCredentials() {
            givenUserIsStored();
            failLogins(WRONG_PASSWORD, 10);

            assertThatThrownBy(() -> service.login(USERNAME, WRONG_PASSWORD, CLIENT_IP))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
        }

        @Test
        void shouldBlockNextLoginAfterNineFailuresSeveralSuccessesAndOneFailure() {
            givenUserIsStored();
            failLogins(WRONG_PASSWORD, 9);
            IntStream.range(0, 3).forEach(attempt -> service.login(USERNAME, PASSWORD, CLIENT_IP));
            failLogins(WRONG_PASSWORD, 1);

            assertThatThrownBy(() -> service.login(USERNAME, PASSWORD, CLIENT_IP))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
        }

        @Test
        void shouldNotComparePasswordWhenPairIsAtTheLimit() {
            givenUserIsStored();
            failLogins(WRONG_PASSWORD, 10);
            clearInvocations(encoder);

            catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            verify(encoder, never()).matches(any(), any());
        }

        @Test
        void shouldAllowLoginAgainSixtyOneSecondsAfterTenFailures() {
            givenUserIsStored();
            failLogins(WRONG_PASSWORD, 10);
            clock.advance(Duration.ofSeconds(61));

            AuthenticationResult result = service.login(USERNAME, PASSWORD, CLIENT_IP);

            assertThat(result.tokens().accessToken()).isNotBlank();
        }

        @Test
        void shouldKeepBlockingLoginFiftyNineSecondsAfterTenFailures() {
            givenUserIsStored();
            failLogins(WRONG_PASSWORD, 10);
            clock.advance(Duration.ofSeconds(59));

            assertThatThrownBy(() -> service.login(USERNAME, PASSWORD, CLIENT_IP))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
        }

        @Test
        void shouldNotRegisterLoginWhenPairIsBlocked() {
            givenUserIsStored();
            IntStream.range(0, 10).forEach(attempt -> limiter.recordFailure(CLIENT_IP, USERNAME));
            int versionBefore = user.tokenVersion();

            catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            assertThat(user.tokenVersion()).isEqualTo(versionBefore);
        }

        @Test
        void shouldNotBlockSameUsernameFromAnotherIpWhenPairIsBlocked() {
            givenUserIsStored();
            failLogins(WRONG_PASSWORD, 10);

            AuthenticationResult result = service.login(USERNAME, PASSWORD, "198.51.100.99");

            assertThat(result.tokens().accessToken()).isNotBlank();
        }
    }

    @Nested
    class PasswordNeverLeaks {

        @Test
        void shouldNotExposePasswordInMessageWhenUserDoesNotExist() {
            givenNoUserIsStored();

            Throwable thrown = catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            assertThat(thrown).isNotNull();
            assertThat(String.valueOf(thrown.getMessage())).doesNotContain(PASSWORD);
            assertThat(thrown.toString()).doesNotContain(PASSWORD);
        }

        @Test
        void shouldNotExposePasswordInMessageWhenPasswordIsWrong() {
            givenUserIsStored();

            Throwable thrown = catchThrowable(() -> service.login(USERNAME, WRONG_PASSWORD, CLIENT_IP));

            assertThat(thrown).isNotNull();
            assertThat(String.valueOf(thrown.getMessage())).doesNotContain(WRONG_PASSWORD);
            assertThat(thrown.toString()).doesNotContain(WRONG_PASSWORD);
        }

        @Test
        void shouldNotExposePasswordInMessageWhenUserIsInactive() {
            givenUserIsStoredAndInactive();

            Throwable thrown = catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            assertThat(thrown).isNotNull();
            assertThat(String.valueOf(thrown.getMessage())).doesNotContain(PASSWORD);
            assertThat(thrown.toString()).doesNotContain(PASSWORD);
        }

        @Test
        void shouldNotExposePasswordInMessageWhenPairIsBlocked() {
            givenUserIsStored();
            IntStream.range(0, 10).forEach(attempt -> limiter.recordFailure(CLIENT_IP, USERNAME));

            Throwable thrown = catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            assertThat(thrown).isNotNull();
            assertThat(String.valueOf(thrown.getMessage())).doesNotContain(PASSWORD);
            assertThat(thrown.toString()).doesNotContain(PASSWORD);
        }
    }

    // ---------------------------------------------------------------- rate limit layers (#56, #59)

    private static final String OTHER_USERNAME = "maria.souza";
    private static final String OTHER_IP = "203.0.113.20";
    private static final long AWAIT_SECONDS = 10;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<CountDownLatch> slotReleases = new ArrayList<>();

    @AfterEach
    void releaseHeldSlotsAndStopThreads() {
        slotReleases.forEach(CountDownLatch::countDown);
        executor.shutdownNow();
    }

    private static LoginRateLimitProperties defaultLimitsWithSlotTimeout(Duration slotTimeout) {
        return new LoginRateLimitProperties(10, 100, Duration.ofMinutes(1), 10_000, 10_000, 3, 30, slotTimeout);
    }

    private InMemoryLoginAttemptRateLimiter newLimiter(LoginRateLimitProperties properties) {
        return new InMemoryLoginAttemptRateLimiter(
                InMemoryLoginAttemptRateLimiter.newFailuresCache(10_000, properties.window(), clock),
                InMemoryLoginAttemptRateLimiter.newFailuresCache(10_000, properties.window(), clock),
                properties,
                clock);
    }

    private AuthenticationService newService(PasswordEncoder passwordEncoder, LoginAttemptRateLimiter rateLimiter) {
        return new AuthenticationService(
                repository, passwordEncoder, tokenIssuer, rateLimiter, TransactionOperations.withoutTransaction(), clock);
    }

    private void givenStored(User storedUser) {
        when(repository.findByUsername(storedUser.username())).thenReturn(Optional.of(storedUser));
        when(repository.findByIdForUpdate(storedUser.id())).thenReturn(Optional.of(storedUser));
    }

    private User otherUser(PasswordEncoder passwordEncoder) {
        return User.create(UUID.randomUUID(), OTHER_USERNAME, "Maria Souza", PASSWORD, Set.of(Role.FRONT_DESK), passwordEncoder);
    }

    private static void recordFailuresOnDistinctUsernames(LoginAttemptRateLimiter rateLimiter, int count) {
        IntStream.range(0, count).forEach(attempt -> rateLimiter.recordFailure(CLIENT_IP, "spray_" + attempt));
    }

    private static String outcomeOf(Supplier<AuthenticationResult> login) {
        try {
            login.get();
            return "SUCCESS";
        } catch (DomainException rejected) {
            return rejected.code();
        } catch (RuntimeException unexpected) {
            return unexpected.getClass().getSimpleName();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(AWAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Parks {@code count} password checks inside slots of {@code ip} until the test ends. */
    private void holdSlots(LoginAttemptRateLimiter rateLimiter, String ip, int count) throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(count);
        CountDownLatch release = new CountDownLatch(1);
        slotReleases.add(release);
        IntStream.range(0, count).forEach(slot -> executor.submit(() -> rateLimiter.runWithPasswordCheckSlot(ip, () -> {
            entered.countDown();
            awaitQuietly(release);
            return null;
        })));
        assertThat(entered.await(AWAIT_SECONDS, TimeUnit.SECONDS)).as("slots should be held").isTrue();
    }

    @Nested
    class PairLayerThroughService {

        @Test
        void shouldNotComparePasswordWhenPairWasBlockedByRecordedFailures() {
            givenUserIsStored();
            IntStream.range(0, 10).forEach(attempt -> limiter.recordFailure(CLIENT_IP, USERNAME));

            catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            verify(encoder, never()).matches(any(), any());
        }

        @Test
        void shouldCountFailuresTypedWithUppercaseLettersForTheNormalizedPair() {
            givenUserIsStored();
            IntStream.range(0, 10)
                    .forEach(attempt -> catchThrowable(() -> service.login("JOAO.SILVA", WRONG_PASSWORD, CLIENT_IP)));

            assertThatThrownBy(() -> service.login(USERNAME, PASSWORD, CLIENT_IP))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
        }

        @Test
        void shouldLetOtherUsernameOfSameIpLogInWhenPairIsBlocked() {
            givenUserIsStored();
            User other = otherUser(encoder);
            givenStored(other);
            failLogins(WRONG_PASSWORD, 10);

            AuthenticationResult result = service.login(OTHER_USERNAME, PASSWORD, CLIENT_IP);

            assertThat(result.tokens().accessToken()).isNotBlank();
        }

        @Test
        void shouldRejectUnknownUsernameWithTooManyAttemptsAfterTenFailures() {
            givenNoUserIsStored();
            failLogins(PASSWORD, 10);

            assertThatThrownBy(() -> service.login(USERNAME, PASSWORD, CLIENT_IP))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
        }
    }

    @Nested
    class IpLayerThroughService {

        @Test
        void shouldRejectNeverUsedUsernameAfterHundredFailuresOfIpOnDistinctUsernames() {
            givenNoUserIsStored();
            IntStream.range(0, 10).forEach(user -> IntStream.range(0, 10)
                    .forEach(attempt -> catchThrowable(() -> service.login("ghost_" + user, WRONG_PASSWORD, CLIENT_IP))));

            assertThatThrownBy(() -> service.login("brand_new_user", PASSWORD, CLIENT_IP))
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::code)
                    .isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        }

        @Test
        void shouldNotRejectNeverUsedUsernameAfterNinetyNineFailuresOfIp() {
            givenNoUserIsStored();
            IntStream.range(0, 99)
                    .forEach(attempt -> catchThrowable(() -> service.login("ghost_" + attempt, WRONG_PASSWORD, CLIENT_IP)));

            assertThatThrownBy(() -> service.login("brand_new_user", PASSWORD, CLIENT_IP))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void shouldRejectCorrectPasswordOfStoredUserWhenIpIsAtTheLimit() {
            givenUserIsStored();
            recordFailuresOnDistinctUsernames(limiter, 100);

            assertThatThrownBy(() -> service.login(USERNAME, PASSWORD, CLIENT_IP))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
        }

        @Test
        void shouldNotComparePasswordWhenIpIsAtTheLimit() {
            givenUserIsStored();
            recordFailuresOnDistinctUsernames(limiter, 100);

            catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            verify(encoder, never()).matches(any(), any());
        }

        @Test
        void shouldLetAnotherIpLogInWhenIpIsAtTheLimit() {
            givenUserIsStored();
            recordFailuresOnDistinctUsernames(limiter, 100);

            AuthenticationResult result = service.login(USERNAME, PASSWORD, OTHER_IP);

            assertThat(result.tokens().accessToken()).isNotBlank();
        }

        @Test
        void shouldNotCountSuccessfulLoginsTowardsIpLimit() {
            givenUserIsStored();
            recordFailuresOnDistinctUsernames(limiter, 99);

            IntStream.range(0, 5).forEach(attempt -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isFalse();
        }

        @Test
        void shouldNotResetIpCounterOnSuccessfulLogin() {
            givenUserIsStored();
            recordFailuresOnDistinctUsernames(limiter, 99);
            service.login(USERNAME, PASSWORD, CLIENT_IP);
            failLogins(WRONG_PASSWORD, 1);

            assertThatThrownBy(() -> service.login(USERNAME, PASSWORD, CLIENT_IP))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
        }
    }

    @Nested
    class PasswordCheckSlotsThroughService {

        private InMemoryLoginAttemptRateLimiter shortTimeoutLimiter;
        private AuthenticationService shortTimeoutService;

        @BeforeEach
        void useShortSlotTimeout() {
            shortTimeoutLimiter = newLimiter(defaultLimitsWithSlotTimeout(Duration.ofMillis(200)));
            shortTimeoutService = newService(encoder, shortTimeoutLimiter);
        }

        @Test
        void shouldMakeDummyComparisonOfUnknownUserWaitForAPasswordCheckSlot() throws InterruptedException {
            givenNoUserIsStored();
            holdSlots(shortTimeoutLimiter, CLIENT_IP, 3);

            assertThatThrownBy(() -> shortTimeoutService.login(USERNAME, PASSWORD, CLIENT_IP))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
        }

        @Test
        void shouldMakeInactiveUserWaitForAPasswordCheckSlot() throws InterruptedException {
            givenUserIsStoredAndInactive();
            holdSlots(shortTimeoutLimiter, CLIENT_IP, 3);

            assertThatThrownBy(() -> shortTimeoutService.login(USERNAME, PASSWORD, CLIENT_IP))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
        }

        @Test
        void shouldRejectCorrectLoginWithTooManyAttemptsWhenSlotWaitTimesOut() throws InterruptedException {
            givenUserIsStored();
            holdSlots(shortTimeoutLimiter, CLIENT_IP, 3);

            assertThatThrownBy(() -> shortTimeoutService.login(USERNAME, PASSWORD, CLIENT_IP))
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::code)
                    .isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        }

        @Test
        void shouldNotComparePasswordOutsideASlot() throws InterruptedException {
            givenNoUserIsStored();
            holdSlots(shortTimeoutLimiter, CLIENT_IP, 3);

            catchThrowable(() -> shortTimeoutService.login(USERNAME, PASSWORD, CLIENT_IP));

            verify(encoder, never()).matches(any(), any());
        }

        @Test
        void shouldAuthenticateFromAnotherIpWhileAllSlotsOfIpAreBusy() throws InterruptedException {
            givenUserIsStored();
            holdSlots(shortTimeoutLimiter, CLIENT_IP, 3);

            AuthenticationResult result = shortTimeoutService.login(USERNAME, PASSWORD, OTHER_IP);

            assertThat(result.tokens().accessToken()).isNotBlank();
        }
    }

    // ---------------------------------------------------------------- concurrent burst (#52)

    /**
     * Real low-cost BCrypt whose comparisons are held at a gate until {@code expectedHolders} of them
     * are running at once, and which records the peak of simultaneous comparisons.
     */
    private static final class GatedPasswordEncoder implements PasswordEncoder {

        private final PasswordEncoder delegate = new BCryptPasswordEncoder(4);
        private final CountDownLatch holders;
        private final CountDownLatch gate = new CountDownLatch(1);
        private final AtomicInteger running = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();

        GatedPasswordEncoder(int expectedHolders) {
            this.holders = new CountDownLatch(expectedHolders);
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            peak.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                holders.countDown();
                awaitQuietly(gate);
                return delegate.matches(rawPassword, encodedPassword);
            } finally {
                running.decrementAndGet();
            }
        }

        boolean awaitHolders() throws InterruptedException {
            return holders.await(AWAIT_SECONDS, TimeUnit.SECONDS);
        }

        void open() {
            gate.countDown();
        }
    }

    private record BurstOutcome(List<String> burstOutcomes, int peakConcurrentComparisons, String otherUserOutcome) {

        int count(String outcome) {
            return Collections.frequency(burstOutcomes, outcome);
        }
    }

    private static final int BURST_SIZE = 30;

    /**
     * {@value #BURST_SIZE} wrong-password logins for the same pair start together; once three
     * comparisons are parked at the gate, a correct login of another username of the same IP is
     * submitted, and only then the gate opens.
     */
    private BurstOutcome runWrongPasswordBurstWithCorrectLoginOfOtherUsername() throws Exception {
        GatedPasswordEncoder gatedEncoder = new GatedPasswordEncoder(3);
        givenStored(User.create(UUID.randomUUID(), USERNAME, "Joao da Silva", PASSWORD, Set.of(Role.WAITER), gatedEncoder));
        givenStored(otherUser(gatedEncoder));
        AuthenticationService burstService =
                newService(gatedEncoder, newLimiter(defaultLimitsWithSlotTimeout(Duration.ofSeconds(30))));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> burst = IntStream.range(0, BURST_SIZE)
                .mapToObj(attempt -> executor.submit(() -> {
                    awaitQuietly(start);
                    return outcomeOf(() -> burstService.login(USERNAME, WRONG_PASSWORD, CLIENT_IP));
                }))
                .toList();

        start.countDown();
        assertThat(gatedEncoder.awaitHolders()).as("three comparisons should be running").isTrue();
        Future<String> otherUserLogin =
                executor.submit(() -> outcomeOf(() -> burstService.login(OTHER_USERNAME, PASSWORD, CLIENT_IP)));
        gatedEncoder.open();

        List<String> outcomes = new ArrayList<>();
        for (Future<String> attempt : burst) {
            outcomes.add(attempt.get(AWAIT_SECONDS * 3, TimeUnit.SECONDS));
        }
        return new BurstOutcome(
                outcomes, gatedEncoder.peak.get(), otherUserLogin.get(AWAIT_SECONDS * 3, TimeUnit.SECONDS));
    }

    @Nested
    class ConcurrentBurstOfFailedLogins {

        @RepeatedTest(3)
        void shouldAnswerBurstOnlyWithInvalidCredentialsOrTooManyAttempts() throws Exception {
            BurstOutcome outcome = runWrongPasswordBurstWithCorrectLoginOfOtherUsername();

            assertThat(outcome.burstOutcomes()).containsOnly("INVALID_CREDENTIALS", "TOO_MANY_LOGIN_ATTEMPTS");
        }

        @RepeatedTest(3)
        void shouldAnswerAtLeastTenAndAtMostTwelveInvalidCredentialsInBurstOfSamePair() throws Exception {
            BurstOutcome outcome = runWrongPasswordBurstWithCorrectLoginOfOtherUsername();

            assertThat(outcome.count("INVALID_CREDENTIALS")).isBetween(10, 12);
        }

        @RepeatedTest(3)
        void shouldAnswerTheRestOfTheBurstWithTooManyAttempts() throws Exception {
            BurstOutcome outcome = runWrongPasswordBurstWithCorrectLoginOfOtherUsername();

            assertThat(outcome.count("TOO_MANY_LOGIN_ATTEMPTS"))
                    .isEqualTo(BURST_SIZE - outcome.count("INVALID_CREDENTIALS"));
        }

        @RepeatedTest(3)
        void shouldNeverRunMoreThanThreePasswordComparisonsOfIpAtOnce() throws Exception {
            BurstOutcome outcome = runWrongPasswordBurstWithCorrectLoginOfOtherUsername();

            assertThat(outcome.peakConcurrentComparisons()).isBetween(1, 3);
        }

        @RepeatedTest(3)
        void shouldLetCorrectLoginOfOtherUsernameOfSameIpSucceedDuringBurst() throws Exception {
            BurstOutcome outcome = runWrongPasswordBurstWithCorrectLoginOfOtherUsername();

            assertThat(outcome.otherUserOutcome()).isEqualTo("SUCCESS");
        }
    }
}
