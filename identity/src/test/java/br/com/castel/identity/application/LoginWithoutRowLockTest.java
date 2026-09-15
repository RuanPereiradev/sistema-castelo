package br.com.castel.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.infra.JwtTokenIssuer;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Written from the "Nenhum lock durante o BCrypt" rule of docs/task-0.4-identity-auth.md and
 * decision #61: the user is read without lock, BCrypt runs outside any transaction, and only a
 * correct password of an active user takes the pessimistic row lock, in a second short
 * transaction. A user removed or deactivated between the read and the lock gets
 * {@code INVALID_CREDENTIALS} and counts as a failure.
 *
 * <p>The repository is an in-memory fake with real per-row locks and a bounded connection pool
 * (see {@link InMemoryLockingUserRepository}); the limiter, the token issuer and the aggregate are
 * real.
 */
class LoginWithoutRowLockTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private static final String SECRET = "test-only-jwt-secret-with-more-than-256-bits-0123456789abcdef";
    private static final String USERNAME = "joao.silva";
    private static final String OTHER_USERNAME = "maria.souza";
    private static final String PASSWORD = "Lock-Canary-Password-42";
    private static final String WRONG_PASSWORD = "Lock-Wrong-Guess-77";
    private static final String CLIENT_IP = "198.51.100.7";
    private static final String OTHER_IP = "203.0.113.20";
    private static final long AWAIT_SECONDS = 10;

    private MutableClock clock;
    private InMemoryLockingUserRepository repository;
    private RecordingLoginAttemptRateLimiter limiter;
    private ObservingPasswordEncoder encoder;
    private TokenIssuer tokenIssuer;
    private AuthenticationService service;
    private User user;
    private ExecutorService executor;
    private final List<ObservingPasswordEncoder> gatedEncoders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        repository = new InMemoryLockingUserRepository(10);
        limiter = RecordingLoginAttemptRateLimiter.inMemory(30, Duration.ofSeconds(30), clock);
        encoder = ObservingPasswordEncoder.observing(
                limiter::isCurrentThreadInsideSlot,
                repository::isCurrentThreadInTransaction,
                repository::isCurrentThreadHoldingRowLock);
        tokenIssuer = spy(new JwtTokenIssuer(SECRET, clock));
        service = serviceWith(repository, encoder);
        user = User.create(UUID.randomUUID(), USERNAME, "Joao da Silva", PASSWORD, Set.of(Role.WAITER), encoder);
        repository.store(user);
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void openGatesAndStopThreads() {
        gatedEncoders.forEach(ObservingPasswordEncoder::open);
        executor.shutdownNow();
    }

    private AuthenticationService serviceWith(InMemoryLockingUserRepository userRepository, ObservingPasswordEncoder passwordEncoder) {
        return new AuthenticationService(
                userRepository, passwordEncoder, tokenIssuer, limiter, userRepository.transactions(), clock);
    }

    private ObservingPasswordEncoder encoderGating(String rawPassword, int expectedHolders) {
        ObservingPasswordEncoder gated = encoder.gating(rawPassword, expectedHolders);
        gatedEncoders.add(gated);
        return gated;
    }

    private Future<String> submitLogin(AuthenticationService loginService, String username, String password, String ip) {
        return executor.submit(() -> outcomeOf(loginService, username, password, ip));
    }

    private static String outcomeOf(AuthenticationService loginService, String username, String password, String ip) {
        try {
            loginService.login(username, password, ip);
            return "SUCCESS";
        } catch (DomainException rejected) {
            return rejected.code();
        } catch (RuntimeException unexpected) {
            return unexpected.getClass().getSimpleName();
        }
    }

    private static List<String> outcomesOf(List<Future<String>> attempts) throws Exception {
        List<String> outcomes = new ArrayList<>();
        for (Future<String> attempt : attempts) {
            outcomes.add(attempt.get(AWAIT_SECONDS * 3, TimeUnit.SECONDS));
        }
        return outcomes;
    }

    @Nested
    class ConcurrentFailuresAgainstExistingUser {

        @Test
        void shouldRunThreeConcurrentWrongPasswordComparisonsOfExistingUserAtTheSameTime() throws Exception {
            ObservingPasswordEncoder gated = encoderGating(WRONG_PASSWORD, 3);
            AuthenticationService gatedService = serviceWith(repository, gated);
            List<Future<String>> attempts = IntStream.range(0, 3)
                    .mapToObj(attempt -> submitLogin(gatedService, USERNAME, WRONG_PASSWORD, CLIENT_IP))
                    .toList();

            boolean threeComparingAtOnce = gated.awaitGatedHolders();
            gated.open();
            outcomesOf(attempts);

            assertThat(threeComparingAtOnce).as("three comparisons should be running at once").isTrue();
            assertThat(gated.peakConcurrentComparisons()).isEqualTo(3);
        }

        @Test
        void shouldAnswerEveryConcurrentWrongPasswordOfExistingUserWithInvalidCredentials() throws Exception {
            ObservingPasswordEncoder gated = encoderGating(WRONG_PASSWORD, 3);
            AuthenticationService gatedService = serviceWith(repository, gated);
            List<Future<String>> attempts = IntStream.range(0, 3)
                    .mapToObj(attempt -> submitLogin(gatedService, USERNAME, WRONG_PASSWORD, CLIENT_IP))
                    .toList();
            gated.awaitGatedHolders();
            gated.open();

            List<String> outcomes = outcomesOf(attempts);

            assertThat(outcomes).containsExactly("INVALID_CREDENTIALS", "INVALID_CREDENTIALS", "INVALID_CREDENTIALS");
        }
    }

    @Nested
    class RowLockOnlyOnSuccess {

        @Test
        void shouldNotTakeRowLockWhenPasswordIsWrong() {
            catchThrowable(() -> service.login(USERNAME, WRONG_PASSWORD, CLIENT_IP));

            assertThat(repository.findByIdForUpdateCalls()).isZero();
        }

        @Test
        void shouldNotTakeRowLockWhenUserIsInactive() {
            user.deactivate();

            catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            assertThat(repository.findByIdForUpdateCalls()).isZero();
        }

        @Test
        void shouldNotTakeRowLockWhenUserDoesNotExist() {
            catchThrowable(() -> service.login("ghost.user", PASSWORD, CLIENT_IP));

            assertThat(repository.findByIdForUpdateCalls()).isZero();
        }

        @Test
        void shouldTakeRowLockExactlyOnceOnSuccessfulLogin() {
            service.login(USERNAME, PASSWORD, CLIENT_IP);

            assertThat(repository.findByIdForUpdateCalls()).isOne();
        }

        @Test
        void shouldSaveRegisteredLoginWhileHoldingTheRowLock() {
            service.login(USERNAME, PASSWORD, CLIENT_IP);

            assertThat(repository.savesHoldingRowLock()).isOne();
        }
    }

    @Nested
    class PasswordComparisonOutsideTransaction {

        @Test
        void shouldNotHoldRowLockWhileComparingWrongPassword() {
            catchThrowable(() -> service.login(USERNAME, WRONG_PASSWORD, CLIENT_IP));

            assertThat(encoder.hashesComputed()).as("a hash must have been computed, or zero proves nothing").isOne();
            assertThat(encoder.comparisonsHoldingRowLock()).isZero();
        }

        @Test
        void shouldNotHoldRowLockWhileComparingCorrectPassword() {
            service.login(USERNAME, PASSWORD, CLIENT_IP);

            assertThat(encoder.hashesComputed()).as("a hash must have been computed, or zero proves nothing").isOne();
            assertThat(encoder.comparisonsHoldingRowLock()).isZero();
        }

        @Test
        void shouldCompareWrongPasswordOutsideAnyTransaction() {
            catchThrowable(() -> service.login(USERNAME, WRONG_PASSWORD, CLIENT_IP));

            assertThat(encoder.hashesComputed()).as("a hash must have been computed, or zero proves nothing").isOne();
            assertThat(encoder.comparisonsInsideTransaction()).isZero();
        }

        @Test
        void shouldCompareCorrectPasswordOutsideAnyTransaction() {
            service.login(USERNAME, PASSWORD, CLIENT_IP);

            assertThat(encoder.hashesComputed()).as("a hash must have been computed, or zero proves nothing").isOne();
            assertThat(encoder.comparisonsInsideTransaction()).isZero();
        }

        @Test
        void shouldCompareDummyPasswordOfUnknownUserOutsideAnyTransaction() {
            catchThrowable(() -> service.login("ghost.user", PASSWORD, CLIENT_IP));

            assertThat(encoder.hashesComputed()).as("a hash must have been computed, or zero proves nothing").isOne();
            assertThat(encoder.comparisonsInsideTransaction()).isZero();
        }
    }

    @Nested
    class ConnectionsDuringBurstOfFailuresFromManyIps {

        private static final int BURST_IPS = 9;

        private List<Future<String>> submitWrongPasswordBurstFromDistinctIps(AuthenticationService burstService) {
            return IntStream.range(0, BURST_IPS)
                    .mapToObj(ip -> submitLogin(burstService, USERNAME, WRONG_PASSWORD, "198.51.100." + (100 + ip)))
                    .toList();
        }

        @Test
        void shouldHoldNoTransactionWhileBurstOfFailedLoginsComparesPasswords() throws Exception {
            ObservingPasswordEncoder gated = encoderGating(WRONG_PASSWORD, BURST_IPS);
            List<Future<String>> burst = submitWrongPasswordBurstFromDistinctIps(serviceWith(repository, gated));
            boolean allComparing = gated.awaitGatedHolders();

            int openTransactionsWhileComparing = repository.openTransactions();
            gated.open();
            outcomesOf(burst);

            assertThat(allComparing).as("every login of the burst should be comparing its password").isTrue();
            assertThat(openTransactionsWhileComparing).isZero();
        }

        @Test
        void shouldLogInAnotherUserThroughSingleConnectionPoolWhileBurstComparesPasswords() throws Exception {
            InMemoryLockingUserRepository singleConnection = new InMemoryLockingUserRepository(1);
            singleConnection.store(user);
            singleConnection.store(
                    User.create(UUID.randomUUID(), OTHER_USERNAME, "Maria Souza", PASSWORD, Set.of(Role.FRONT_DESK), encoder));
            ObservingPasswordEncoder gated = encoderGating(WRONG_PASSWORD, BURST_IPS);
            AuthenticationService burstService = serviceWith(singleConnection, gated);
            List<Future<String>> burst = submitWrongPasswordBurstFromDistinctIps(burstService);
            gated.awaitGatedHolders();

            String otherUserOutcome = submitLogin(burstService, OTHER_USERNAME, PASSWORD, OTHER_IP)
                    .get(AWAIT_SECONDS, TimeUnit.SECONDS);
            gated.open();
            outcomesOf(burst);

            assertThat(otherUserOutcome).isEqualTo("SUCCESS");
        }
    }

    @Nested
    class UserChangedBetweenReadAndLock {

        @Test
        void shouldRejectWithInvalidCredentialsWhenUserIsRemovedBeforeTheLock() {
            repository.removeBeforeNextRowLock(user.id());

            assertThatThrownBy(() -> service.login(USERNAME, PASSWORD, CLIENT_IP))
                    .asInstanceOf(type(InvalidCredentialsException.class))
                    .extracting(InvalidCredentialsException::code)
                    .isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        void shouldCountRemovalBeforeTheLockAsFailure() {
            repository.removeBeforeNextRowLock(user.id());

            catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            assertThat(limiter.recordedFailures()).isOne();
        }

        @Test
        void shouldNotIssueTokensWhenUserIsRemovedBeforeTheLock() {
            repository.removeBeforeNextRowLock(user.id());

            catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            verify(tokenIssuer, never()).issueTokenPair(any());
        }

        @Test
        void shouldRejectWithInvalidCredentialsWhenUserIsDeactivatedBeforeTheLock() {
            repository.deactivateBeforeNextRowLock(user.id());

            assertThatThrownBy(() -> service.login(USERNAME, PASSWORD, CLIENT_IP))
                    .asInstanceOf(type(InvalidCredentialsException.class))
                    .extracting(InvalidCredentialsException::code)
                    .isEqualTo("INVALID_CREDENTIALS");
        }

        @Test
        void shouldCountDeactivationBeforeTheLockAsFailure() {
            repository.deactivateBeforeNextRowLock(user.id());

            catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            assertThat(limiter.recordedFailures()).isOne();
        }

        @Test
        void shouldNotIssueTokensWhenUserIsDeactivatedBeforeTheLock() {
            repository.deactivateBeforeNextRowLock(user.id());

            catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            verify(tokenIssuer, never()).issueTokenPair(any());
        }

        @Test
        void shouldNotRegisterLoginWhenUserIsDeactivatedBeforeTheLock() {
            repository.deactivateBeforeNextRowLock(user.id());

            catchThrowable(() -> service.login(USERNAME, PASSWORD, CLIENT_IP));

            assertThat(user.lastLoginAt()).isNull();
        }
    }
}
