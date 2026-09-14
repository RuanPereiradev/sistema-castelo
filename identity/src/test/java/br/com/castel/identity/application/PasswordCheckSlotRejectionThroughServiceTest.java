package br.com.castel.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.castel.identity.api.Role;
import br.com.castel.identity.application.TooManyLoginAttemptsException.Reason;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import br.com.castel.identity.infra.JwtTokenIssuer;
import br.com.castel.sharedkernel.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Written from the "Proteção de CPU" rule of docs/task-0.4-identity-auth.md and decisions #64,
 * #65 and #68, through the service: slot timeout, waiting limit and interrupted wait answer 429
 * with their own {@link Reason}, and none of them counts as a failure in either layer.
 *
 * <p>"Not a failure" is proven by the outcome: with the pair at 9 failures (or the IP at 99), a
 * rejection that counted would make the next correct login a 429 instead of a success.
 */
class PasswordCheckSlotRejectionThroughServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private static final String SECRET = "test-only-jwt-secret-with-more-than-256-bits-0123456789abcdef";
    private static final String USERNAME = "joao.silva";
    private static final String PASSWORD = "Slot-Canary-Password-42";
    private static final String CLIENT_IP = "198.51.100.7";
    private static final long AWAIT_SECONDS = 10;

    private MutableClock clock;
    private UserRepository repository;
    private ObservingPasswordEncoder encoder;
    private ExecutorService executor;
    private RecordingLoginAttemptRateLimiter shortTimeout;
    private RecordingLoginAttemptRateLimiter oneWaitingLongTimeout;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        repository = mock(UserRepository.class);
        encoder = ObservingPasswordEncoder.unobserved();
        executor = Executors.newCachedThreadPool();
        shortTimeout = RecordingLoginAttemptRateLimiter.inMemory(30, Duration.ofMillis(200), clock);
        oneWaitingLongTimeout = RecordingLoginAttemptRateLimiter.inMemory(1, Duration.ofSeconds(AWAIT_SECONDS), clock);
        User user = User.create(UUID.randomUUID(), USERNAME, "Joao da Silva", PASSWORD, Set.of(Role.WAITER), encoder);
        when(repository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(repository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).then(returnsFirstArg());
    }

    @AfterEach
    void releaseSlotsAndStopThreads() {
        shortTimeout.releaseHeldSlots();
        oneWaitingLongTimeout.releaseHeldSlots();
        executor.shutdownNow();
    }

    private AuthenticationService serviceWith(LoginAttemptRateLimiter limiter) {
        return new AuthenticationService(
                repository, encoder, new JwtTokenIssuer(SECRET, clock), limiter,
                TransactionOperations.withoutTransaction(), clock);
    }

    /** Holds the 3 slots, lets a login time out, then frees the slots. */
    private Throwable loginTimingOutForSlot() throws InterruptedException {
        shortTimeout.holdSlots(CLIENT_IP, 3, executor);
        Throwable rejection = catchThrowable(() -> serviceWith(shortTimeout).login(USERNAME, PASSWORD, CLIENT_IP));
        shortTimeout.releaseHeldSlots();
        return rejection;
    }

    /** Holds the 3 slots and parks one waiter, so the waiting limit (1) is reached; then frees everything. */
    private Throwable loginBeyondWaitingLimit() throws Exception {
        oneWaitingLongTimeout.holdSlots(CLIENT_IP, 3, executor);
        AtomicReference<Thread> waiterThread = new AtomicReference<>();
        Future<Throwable> waiter = executor.submit(() -> {
            waiterThread.set(Thread.currentThread());
            return catchThrowable(() -> oneWaitingLongTimeout.runWithPasswordCheckSlot(CLIENT_IP, () -> null));
        });
        RecordingLoginAttemptRateLimiter.awaitWaiting(waiterThread);
        Throwable rejection = catchThrowable(() -> serviceWith(oneWaitingLongTimeout).login(USERNAME, PASSWORD, CLIENT_IP));
        oneWaitingLongTimeout.releaseHeldSlots();
        waiter.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        return rejection;
    }

    /** Holds the 3 slots, interrupts a login waiting for one, then frees the slots. */
    private Throwable loginInterruptedWhileWaitingForSlot() throws Exception {
        oneWaitingLongTimeout.holdSlots(CLIENT_IP, 3, executor);
        AtomicReference<Thread> loginThread = new AtomicReference<>();
        Future<Throwable> login = executor.submit(() -> {
            loginThread.set(Thread.currentThread());
            return catchThrowable(() -> serviceWith(oneWaitingLongTimeout).login(USERNAME, PASSWORD, CLIENT_IP));
        });
        RecordingLoginAttemptRateLimiter.awaitWaiting(loginThread);
        loginThread.get().interrupt();
        Throwable rejection = login.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        oneWaitingLongTimeout.releaseHeldSlots();
        return rejection;
    }

    @Nested
    class Reasons {

        @Test
        void shouldRejectLoginWhoseSlotWaitTimesOutWithSlotTimeoutReason() throws InterruptedException {
            Throwable rejection = loginTimingOutForSlot();

            assertThat(rejection)
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::reason)
                    .isEqualTo(Reason.PASSWORD_CHECK_SLOT_TIMEOUT);
        }

        @Test
        void shouldRejectLoginBeyondWaitingLimitWithWaitingLimitReason() throws Exception {
            Throwable rejection = loginBeyondWaitingLimit();

            assertThat(rejection)
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::reason)
                    .isEqualTo(Reason.WAITING_LIMIT_REACHED);
        }

        @Test
        void shouldRejectInterruptedLoginWithInterruptedWaitReason() throws Exception {
            Throwable rejection = loginInterruptedWhileWaitingForSlot();

            assertThat(rejection)
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::reason)
                    .isEqualTo(Reason.PASSWORD_CHECK_SLOT_WAIT_INTERRUPTED);
        }

        @Test
        void shouldRejectBlockedPairWithFailureLimitReason() {
            shortTimeout.recordFailuresWithoutLogin(CLIENT_IP, USERNAME, 10);

            assertThatThrownBy(() -> serviceWith(shortTimeout).login(USERNAME, PASSWORD, CLIENT_IP))
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::reason)
                    .isEqualTo(Reason.FAILURE_LIMIT_REACHED);
        }

        @Test
        void shouldAnswerSlotTimeoutWithTooManyLoginAttemptsCode() throws Exception {
            Throwable timeout = loginTimingOutForSlot();

            assertThat(timeout)
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::code)
                    .isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        }
    }

    @Nested
    class SlotTimeoutIsNotAFailure {

        @Test
        void shouldNotCountSlotTimeoutAsPairFailure() throws InterruptedException {
            shortTimeout.recordFailuresWithoutLogin(CLIENT_IP, USERNAME, 9);
            loginTimingOutForSlot();

            AuthenticationResult result = serviceWith(shortTimeout).login(USERNAME, PASSWORD, CLIENT_IP);

            assertThat(result.tokens().accessToken()).isNotBlank();
        }

        @Test
        void shouldNotCountSlotTimeoutAsIpFailure() throws InterruptedException {
            shortTimeout.recordFailuresOnDistinctUsernamesWithoutLogin(CLIENT_IP, 99);
            loginTimingOutForSlot();

            AuthenticationResult result = serviceWith(shortTimeout).login(USERNAME, PASSWORD, CLIENT_IP);

            assertThat(result.tokens().accessToken()).isNotBlank();
        }

        @Test
        void shouldNotComparePasswordOfLoginWhoseSlotWaitTimedOut() throws InterruptedException {
            loginTimingOutForSlot();

            assertThat(encoder.comparisons()).isZero();
        }
    }

    @Nested
    class WaitingLimitIsNotAFailure {

        @Test
        void shouldNotCountWaitingLimitRejectionAsPairFailure() throws Exception {
            oneWaitingLongTimeout.recordFailuresWithoutLogin(CLIENT_IP, USERNAME, 9);
            loginBeyondWaitingLimit();

            AuthenticationResult result = serviceWith(oneWaitingLongTimeout).login(USERNAME, PASSWORD, CLIENT_IP);

            assertThat(result.tokens().accessToken()).isNotBlank();
        }

        @Test
        void shouldNotCountWaitingLimitRejectionAsIpFailure() throws Exception {
            oneWaitingLongTimeout.recordFailuresOnDistinctUsernamesWithoutLogin(CLIENT_IP, 99);
            loginBeyondWaitingLimit();

            AuthenticationResult result = serviceWith(oneWaitingLongTimeout).login(USERNAME, PASSWORD, CLIENT_IP);

            assertThat(result.tokens().accessToken()).isNotBlank();
        }

        @Test
        void shouldNotComparePasswordOfLoginRejectedByWaitingLimit() throws Exception {
            loginBeyondWaitingLimit();

            assertThat(encoder.comparisons()).isZero();
        }
    }

    @Nested
    class InterruptedWaitIsNotAFailure {

        @Test
        void shouldNotCountInterruptedSlotWaitAsPairFailure() throws Exception {
            oneWaitingLongTimeout.recordFailuresWithoutLogin(CLIENT_IP, USERNAME, 9);
            loginInterruptedWhileWaitingForSlot();

            AuthenticationResult result = serviceWith(oneWaitingLongTimeout).login(USERNAME, PASSWORD, CLIENT_IP);

            assertThat(result.tokens().accessToken()).isNotBlank();
        }

        @Test
        void shouldNotCountInterruptedSlotWaitAsIpFailure() throws Exception {
            oneWaitingLongTimeout.recordFailuresOnDistinctUsernamesWithoutLogin(CLIENT_IP, 99);
            loginInterruptedWhileWaitingForSlot();

            AuthenticationResult result = serviceWith(oneWaitingLongTimeout).login(USERNAME, PASSWORD, CLIENT_IP);

            assertThat(result.tokens().accessToken()).isNotBlank();
        }
    }
}
