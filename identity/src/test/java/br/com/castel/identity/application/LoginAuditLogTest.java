package br.com.castel.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.castel.identity.api.Role;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Written from the "Log de auditoria" rule of docs/task-0.4-identity-auth.md and decision #69:
 * every failure and every 429 of the login is logged with the IP and always the attempted username
 * (unknown and malformed included), quoted, escaped and truncated at 64 code points. Never a
 * password, hash or token.
 *
 * <p>The message wording is not specified, so only the quoted username, the IP and the absence of
 * secrets are asserted.
 */
@ExtendWith(OutputCaptureExtension.class)
class LoginAuditLogTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private static final String SECRET = "test-only-jwt-secret-with-more-than-256-bits-0123456789abcdef";
    private static final String PASSWORD = "Audit_Canary_Password_42";
    private static final String WRONG_PASSWORD = "Audit_Canary_Wrong_77";
    private static final String CLIENT_IP = "198.51.100.61";
    private static final String BCRYPT_PREFIX = "$2a$";
    private static final String JWT_PREFIX = "eyJ";

    private MutableClock clock;
    private UserRepository repository;
    private ObservingPasswordEncoder encoder;
    private ExecutorService executor;
    private RecordingLoginAttemptRateLimiter limiter;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        repository = mock(UserRepository.class);
        encoder = ObservingPasswordEncoder.unobserved();
        executor = Executors.newCachedThreadPool();
        limiter = RecordingLoginAttemptRateLimiter.inMemory(30, Duration.ofSeconds(5), clock);
        service = serviceWith(limiter);
        when(repository.findByUsername(anyString())).thenReturn(Optional.empty());
    }

    @AfterEach
    void releaseSlotsAndStopThreads() {
        limiter.releaseHeldSlots();
        executor.shutdownNow();
    }

    private AuthenticationService serviceWith(LoginAttemptRateLimiter rateLimiter) {
        return new AuthenticationService(
                repository, encoder, new JwtTokenIssuer(SECRET, clock), rateLimiter,
                TransactionOperations.withoutTransaction(), clock);
    }

    private void givenStored(String username) {
        User user = User.create(UUID.randomUUID(), username, "Audited User", PASSWORD, Set.of(Role.WAITER), encoder);
        when(repository.findByUsername(username)).thenReturn(Optional.of(user));
        when(repository.findByIdForUpdate(user.id())).thenReturn(Optional.of(user));
        when(repository.save(any(User.class))).then(returnsFirstArg());
    }

    private static String quoted(String username) {
        return "\"" + username + "\"";
    }

    private static boolean hasLineWith(CapturedOutput output, String first, String second) {
        return output.getAll().lines().anyMatch(line -> line.contains(first) && line.contains(second));
    }

    /** Logs in on a pool thread and waits until that thread is parked waiting for a slot. */
    private AtomicReference<Thread> startLoginWaitingForSlot(AuthenticationService loginService, String username)
            throws InterruptedException {
        AtomicReference<Thread> thread = new AtomicReference<>();
        executor.submit(() -> {
            thread.set(Thread.currentThread());
            return catchThrowable(() -> loginService.login(username, PASSWORD, CLIENT_IP));
        });
        RecordingLoginAttemptRateLimiter.awaitWaiting(thread);
        return thread;
    }

    private AtomicReference<Thread> parkOneWaiterDirectlyOnLimiter(RecordingLoginAttemptRateLimiter rateLimiter) {
        AtomicReference<Thread> thread = new AtomicReference<>();
        executor.submit(() -> {
            thread.set(Thread.currentThread());
            return catchThrowable(() -> rateLimiter.runWithPasswordCheckSlot(CLIENT_IP, () -> null));
        });
        RecordingLoginAttemptRateLimiter.awaitWaiting(thread);
        return thread;
    }

    @Nested
    class AttemptedUsername {

        @Test
        void shouldLogQuotedUsernameOfUnknownWellFormedUser(CapturedOutput output) {
            catchThrowable(() -> service.login("ghost.audit", WRONG_PASSWORD, CLIENT_IP));

            assertThat(output.getAll()).contains(quoted("ghost.audit"));
        }

        @Test
        void shouldLogClientIpTogetherWithUsernameOfFailedLogin(CapturedOutput output) {
            catchThrowable(() -> service.login("ghost.audit", WRONG_PASSWORD, CLIENT_IP));

            assertThat(hasLineWith(output, quoted("ghost.audit"), CLIENT_IP)).isTrue();
        }

        @Test
        void shouldLogQuotedUsernameOfMalformedUsername(CapturedOutput output) {
            catchThrowable(() -> service.login("ghost@audit", WRONG_PASSWORD, CLIENT_IP));

            assertThat(output.getAll()).contains(quoted("ghost@audit"));
        }
    }

    @Nested
    class InjectionAndLength {

        private static final String INJECTED = "FORGED_AUDIT_LINE_4b1e";

        @Test
        void shouldNotStartAnyLogLineWithTextInjectedThroughLineFeed(CapturedOutput output) {
            catchThrowable(() -> service.login("ghost\n" + INJECTED, WRONG_PASSWORD, CLIENT_IP));

            assertThat(output.getAll().lines()).noneMatch(line -> line.startsWith(INJECTED));
        }

        @Test
        void shouldNotStartAnyLogLineWithTextInjectedThroughCarriageReturn(CapturedOutput output) {
            catchThrowable(() -> service.login("ghost\r" + INJECTED, WRONG_PASSWORD, CLIENT_IP));

            assertThat(output.getAll().lines()).noneMatch(line -> line.startsWith(INJECTED));
        }

        @Test
        void shouldKeepTextInjectedThroughLineFeedOnTheLogLineOfTheAttempt(CapturedOutput output) {
            catchThrowable(() -> service.login("ghost_lf\n" + INJECTED, WRONG_PASSWORD, CLIENT_IP));

            assertThat(hasLineWith(output, "ghost_lf", INJECTED)).isTrue();
        }

        @Test
        void shouldNotLogFiveThousandCharacterUsernameInFull(CapturedOutput output) {
            String firstSixtyFour = "long_" + "x".repeat(59);
            String username = firstSixtyFour + "y".repeat(4_936);

            catchThrowable(() -> service.login(username, WRONG_PASSWORD, CLIENT_IP));

            assertThat(output.getAll()).doesNotContain(firstSixtyFour + "y");
        }

        @Test
        void shouldLogFirstSixtyFourCodePointsOfFiveThousandCharacterUsernameBetweenQuotes(CapturedOutput output) {
            String firstSixtyFour = "long_" + "x".repeat(59);
            String username = firstSixtyFour + "y".repeat(4_936);

            catchThrowable(() -> service.login(username, WRONG_PASSWORD, CLIENT_IP));

            assertThat(output.getAll()).contains(quoted(firstSixtyFour));
        }
    }

    @Nested
    class TooManyAttemptsRejections {

        @Test
        void shouldLogFailureLimitRejectionWithIpAndUsername(CapturedOutput output) {
            limiter.recordFailuresWithoutLogin(CLIENT_IP, "blocked.audit", 10);

            catchThrowable(() -> service.login("blocked.audit", PASSWORD, CLIENT_IP));

            assertThat(hasLineWith(output, quoted("blocked.audit"), CLIENT_IP)).isTrue();
        }

        @Test
        void shouldLogSlotTimeoutRejectionWithIpAndUsername(CapturedOutput output) throws InterruptedException {
            RecordingLoginAttemptRateLimiter shortTimeout =
                    RecordingLoginAttemptRateLimiter.inMemory(30, Duration.ofMillis(200), clock);
            shortTimeout.holdSlots(CLIENT_IP, 3, executor);

            catchThrowable(() -> serviceWith(shortTimeout).login("timeout.audit", PASSWORD, CLIENT_IP));
            shortTimeout.releaseHeldSlots();

            assertThat(hasLineWith(output, quoted("timeout.audit"), CLIENT_IP)).isTrue();
        }

        @Test
        void shouldLogWaitingLimitRejectionWithIpAndUsername(CapturedOutput output) throws InterruptedException {
            RecordingLoginAttemptRateLimiter oneWaiting =
                    RecordingLoginAttemptRateLimiter.inMemory(1, Duration.ofSeconds(10), clock);
            oneWaiting.holdSlots(CLIENT_IP, 3, executor);
            parkOneWaiterDirectlyOnLimiter(oneWaiting);

            catchThrowable(() -> serviceWith(oneWaiting).login("waiting.audit", PASSWORD, CLIENT_IP));
            oneWaiting.releaseHeldSlots();

            assertThat(hasLineWith(output, quoted("waiting.audit"), CLIENT_IP)).isTrue();
        }

        @Test
        void shouldLogInterruptedSlotWaitRejectionWithIpAndUsername(CapturedOutput output) throws Exception {
            RecordingLoginAttemptRateLimiter longWait =
                    RecordingLoginAttemptRateLimiter.inMemory(30, Duration.ofSeconds(10), clock);
            longWait.holdSlots(CLIENT_IP, 3, executor);
            AtomicReference<Thread> waiting = startLoginWaitingForSlot(serviceWith(longWait), "interrupted.audit");

            waiting.get().interrupt();
            longWait.releaseHeldSlots();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            assertThat(hasLineWith(output, quoted("interrupted.audit"), CLIENT_IP)).isTrue();
        }
    }

    @Nested
    class NoSecrets {

        @Test
        void shouldNotLogPasswordHashOrTokenOnSuccessfulLogin(CapturedOutput output) {
            givenStored("success.audit");

            service.login("success.audit", PASSWORD, CLIENT_IP);

            assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(BCRYPT_PREFIX).doesNotContain(JWT_PREFIX);
        }

        @Test
        void shouldNotLogPasswordHashOrTokenOnWrongPassword(CapturedOutput output) {
            givenStored("wrong.audit");

            catchThrowable(() -> service.login("wrong.audit", WRONG_PASSWORD, CLIENT_IP));

            assertThat(output.getAll()).doesNotContain(WRONG_PASSWORD).doesNotContain(BCRYPT_PREFIX).doesNotContain(JWT_PREFIX);
        }

        @Test
        void shouldNotLogPasswordHashOrTokenOnUnknownUser(CapturedOutput output) {
            catchThrowable(() -> service.login("ghost.audit", PASSWORD, CLIENT_IP));

            assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(BCRYPT_PREFIX).doesNotContain(JWT_PREFIX);
        }

        @Test
        void shouldNotLogPasswordHashOrTokenOnMalformedUsername(CapturedOutput output) {
            catchThrowable(() -> service.login("ghost audit", PASSWORD, CLIENT_IP));

            assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(BCRYPT_PREFIX).doesNotContain(JWT_PREFIX);
        }

        @Test
        void shouldNotLogPasswordHashOrTokenOnFailureLimitRejection(CapturedOutput output) {
            givenStored("blocked.audit");
            limiter.recordFailuresWithoutLogin(CLIENT_IP, "blocked.audit", 10);

            catchThrowable(() -> service.login("blocked.audit", PASSWORD, CLIENT_IP));

            assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(BCRYPT_PREFIX).doesNotContain(JWT_PREFIX);
        }

        @Test
        void shouldNotLogPasswordHashOrTokenOnSlotTimeoutRejection(CapturedOutput output) throws InterruptedException {
            givenStored("timeout.audit");
            RecordingLoginAttemptRateLimiter shortTimeout =
                    RecordingLoginAttemptRateLimiter.inMemory(30, Duration.ofMillis(200), clock);
            shortTimeout.holdSlots(CLIENT_IP, 3, executor);

            catchThrowable(() -> serviceWith(shortTimeout).login("timeout.audit", PASSWORD, CLIENT_IP));
            shortTimeout.releaseHeldSlots();

            assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(BCRYPT_PREFIX).doesNotContain(JWT_PREFIX);
        }

        @Test
        void shouldNotLogPasswordHashOrTokenOnWaitingLimitRejection(CapturedOutput output) throws InterruptedException {
            givenStored("waiting.audit");
            RecordingLoginAttemptRateLimiter oneWaiting =
                    RecordingLoginAttemptRateLimiter.inMemory(1, Duration.ofSeconds(10), clock);
            oneWaiting.holdSlots(CLIENT_IP, 3, executor);
            parkOneWaiterDirectlyOnLimiter(oneWaiting);

            catchThrowable(() -> serviceWith(oneWaiting).login("waiting.audit", PASSWORD, CLIENT_IP));
            oneWaiting.releaseHeldSlots();

            assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(BCRYPT_PREFIX).doesNotContain(JWT_PREFIX);
        }
    }
}
