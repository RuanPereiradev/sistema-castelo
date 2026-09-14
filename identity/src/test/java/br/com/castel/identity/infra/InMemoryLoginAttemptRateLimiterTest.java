package br.com.castel.identity.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import static org.assertj.core.api.InstanceOfAssertFactories.type;

import br.com.castel.identity.application.TooManyLoginAttemptsException;
import br.com.castel.identity.application.TooManyLoginAttemptsException.Reason;
import br.com.castel.identity.domain.User;
import br.com.castel.sharedkernel.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Written from the "Limite de tentativas" and "Proteção de CPU" rules of
 * docs/task-0.4-identity-auth.md and decisions #56, #57 and #59 of docs/decisions/task-0.4.md.
 *
 * <p>Time is moved with {@link MutableClock}. Concurrency is proven with latches and thread states,
 * never with sleeps whose length decides the outcome.
 */
class InMemoryLoginAttemptRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private static final String CLIENT_IP = "198.51.100.7";
    private static final String OTHER_IP = "203.0.113.20";
    private static final String USERNAME = "joao.silva";
    private static final String OTHER_USERNAME = "maria.souza";
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration GENEROUS_SLOT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SHORT_SLOT_TIMEOUT = Duration.ofMillis(200);
    /** Larger than any burst in this class, so the waiting limit (#68) never interferes here. */
    private static final int GENEROUS_WAITING_LIMIT = 10_000;
    private static final long AWAIT_SECONDS = 10;

    private MutableClock clock;
    private ExecutorService executor;
    private final List<HeldSlots> heldSlots = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        heldSlots.forEach(HeldSlots::releaseAll);
        executor.shutdownNow();
    }

    // ---------------------------------------------------------------- fixtures

    private static LoginRateLimitProperties properties(
            int maxFailuresPerUsername, int maxFailuresPerIp, int maxConcurrentChecks, Duration slotTimeout) {
        return new LoginRateLimitProperties(
                maxFailuresPerUsername, maxFailuresPerIp, WINDOW, 10_000, 10_000, maxConcurrentChecks,
                GENEROUS_WAITING_LIMIT, slotTimeout);
    }

    private InMemoryLoginAttemptRateLimiter limiterWith(LoginRateLimitProperties properties) {
        return new InMemoryLoginAttemptRateLimiter(
                InMemoryLoginAttemptRateLimiter.newFailuresCache(properties.maxTrackedPairs(), properties.window(), clock),
                InMemoryLoginAttemptRateLimiter.newFailuresCache(properties.maxTrackedIps(), properties.window(), clock),
                properties,
                clock);
    }

    private InMemoryLoginAttemptRateLimiter defaultLimiter() {
        return limiterWith(properties(10, 100, 3, GENEROUS_SLOT_TIMEOUT));
    }

    private InMemoryLoginAttemptRateLimiter limiterWithShortSlotTimeout() {
        return limiterWith(properties(10, 100, 3, SHORT_SLOT_TIMEOUT));
    }

    private static void recordFailures(InMemoryLoginAttemptRateLimiter limiter, String ip, String username, int count) {
        IntStream.range(0, count).forEach(attempt -> limiter.recordFailure(ip, username));
    }

    /** One failure per distinct username, so layer 1 never interferes with layer 2. */
    private static void recordFailuresOnDistinctUsernames(InMemoryLoginAttemptRateLimiter limiter, String ip, int count) {
        IntStream.range(0, count).forEach(attempt -> limiter.recordFailure(ip, "spray_" + attempt));
    }

    /** Password checks parked inside their slot until released one by one. */
    private final class HeldSlots {

        private final List<CountDownLatch> releases = new ArrayList<>();

        void releaseOne() {
            releases.removeFirst().countDown();
        }

        void releaseAll() {
            releases.forEach(CountDownLatch::countDown);
            releases.clear();
        }
    }

    private HeldSlots holdSlots(InMemoryLoginAttemptRateLimiter limiter, String ip, int count) throws InterruptedException {
        HeldSlots held = new HeldSlots();
        heldSlots.add(held);
        CountDownLatch entered = new CountDownLatch(count);
        IntStream.range(0, count).forEach(slot -> {
            CountDownLatch release = new CountDownLatch(1);
            held.releases.add(release);
            executor.submit(() -> limiter.runWithPasswordCheckSlot(ip, () -> {
                entered.countDown();
                awaitQuietly(release);
                return null;
            }));
        });
        assertThat(entered.await(AWAIT_SECONDS, TimeUnit.SECONDS))
                .as("all %d password checks should have obtained a slot", count)
                .isTrue();
        return held;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(AWAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Waits until the thread is parked (waiting for a slot), failing after a generous deadline. */
    private static void awaitParked(AtomicReference<Thread> threadReference) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (!isParked(threadReference.get()) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(isParked(threadReference.get())).as("fourth password check should be waiting for a slot").isTrue();
    }

    private static boolean isParked(Thread thread) {
        return thread != null
                && (thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING);
    }

    // ---------------------------------------------------------------- layer 1

    @Nested
    class PairLayer {

        @Test
        void shouldNotBlockPairAfterNineFailures() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, USERNAME, 9);

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isFalse();
        }

        @Test
        void shouldBlockPairAfterTenFailures() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, USERNAME, 10);

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isTrue();
        }

        @Test
        void shouldKeepOtherUsernameOfSameIpFreeWhenPairIsBlocked() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, USERNAME, 10);

            assertThat(limiter.isBlocked(CLIENT_IP, OTHER_USERNAME)).isFalse();
        }

        @Test
        void shouldKeepSameUsernameFromOtherIpFreeWhenPairIsBlocked() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, USERNAME, 10);

            assertThat(limiter.isBlocked(OTHER_IP, USERNAME)).isFalse();
        }

        @Test
        void shouldBlockLowercaseUsernameAfterFailuresRecordedWithUppercaseLetters() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, "Joao.Silva", 10);

            assertThat(limiter.isBlocked(CLIENT_IP, "joao.silva")).isTrue();
        }

        @Test
        void shouldSumFailuresOfDifferentCaseVariantsOfTheSameUsername() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, "joao.silva", 5);
            recordFailures(limiter, CLIENT_IP, "JOAO.SILVA", 5);

            assertThat(limiter.isBlocked(CLIENT_IP, "Joao.Silva")).isTrue();
        }

        @Test
        void shouldBlockUsernameThatDoesNotExistLikeAnyOther() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, "ghost_that_never_existed", 10);

            assertThat(limiter.isBlocked(CLIENT_IP, "ghost_that_never_existed")).isTrue();
        }
    }

    // ---------------------------------------------------------------- layer 2

    @Nested
    class IpLayer {

        @Test
        void shouldNotBlockIpAfterNinetyNineFailuresOnDistinctUsernames() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailuresOnDistinctUsernames(limiter, CLIENT_IP, 99);

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isFalse();
        }

        @Test
        void shouldBlockNeverUsedUsernameOfIpAfterHundredFailuresOnDistinctUsernames() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailuresOnDistinctUsernames(limiter, CLIENT_IP, 100);

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isTrue();
        }

        @Test
        void shouldSumFailuresOfAllUsernamesOfTheIp() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            IntStream.range(0, 10).forEach(user -> recordFailures(limiter, CLIENT_IP, "user_" + user, 10));

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isTrue();
        }

        @Test
        void shouldKeepOtherIpFreeWhenIpIsBlocked() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailuresOnDistinctUsernames(limiter, CLIENT_IP, 100);

            assertThat(limiter.isBlocked(OTHER_IP, "never_used")).isFalse();
        }
    }

    // ---------------------------------------------------------------- window

    @Nested
    class SlidingWindow {

        @Test
        void shouldKeepPairBlockedFiftyNineSecondsAfterTenthFailure() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, USERNAME, 10);
            clock.advance(Duration.ofSeconds(59));

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isTrue();
        }

        @Test
        void shouldReleasePairSixtyOneSecondsAfterTenthFailure() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, USERNAME, 10);
            clock.advance(Duration.ofSeconds(61));

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isFalse();
        }

        @Test
        void shouldKeepIpBlockedFiftyNineSecondsAfterHundredthFailure() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailuresOnDistinctUsernames(limiter, CLIENT_IP, 100);
            clock.advance(Duration.ofSeconds(59));

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isTrue();
        }

        @Test
        void shouldReleaseIpSixtyOneSecondsAfterHundredthFailure() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailuresOnDistinctUsernames(limiter, CLIENT_IP, 100);
            clock.advance(Duration.ofSeconds(61));

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isFalse();
        }

        @Test
        void shouldReleasePairWhenOnlyOldestFailuresLeaveTheWindow() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, USERNAME, 5);
            clock.advance(Duration.ofSeconds(30));
            recordFailures(limiter, CLIENT_IP, USERNAME, 5);
            clock.advance(Duration.ofSeconds(31));

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isFalse();
        }

        @Test
        void shouldBlockPairWithFailuresThatStraddleAMinuteBoundaryButFitInOneWindow() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            clock.advance(Duration.ofSeconds(45));
            recordFailures(limiter, CLIENT_IP, USERNAME, 5);
            clock.advance(Duration.ofSeconds(30));
            recordFailures(limiter, CLIENT_IP, USERNAME, 5);

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isTrue();
        }

        @Test
        void shouldBlockIpWithFailuresThatStraddleAMinuteBoundaryButFitInOneWindow() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            clock.advance(Duration.ofSeconds(45));
            IntStream.range(0, 50).forEach(attempt -> limiter.recordFailure(CLIENT_IP, "first_half_" + attempt));
            clock.advance(Duration.ofSeconds(30));
            IntStream.range(0, 50).forEach(attempt -> limiter.recordFailure(CLIENT_IP, "second_half_" + attempt));

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isTrue();
        }

        @Test
        void shouldReleaseIpWhenOnlyOldestFailuresLeaveTheWindow() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            IntStream.range(0, 50).forEach(attempt -> limiter.recordFailure(CLIENT_IP, "first_half_" + attempt));
            clock.advance(Duration.ofSeconds(30));
            IntStream.range(0, 50).forEach(attempt -> limiter.recordFailure(CLIENT_IP, "second_half_" + attempt));
            clock.advance(Duration.ofSeconds(31));

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isFalse();
        }
    }

    // ---------------------------------------------------------------- configuration

    @Nested
    class ConfigurableLimits {

        @Test
        void shouldBlockPairAtConfiguredLimitBelowDefault() {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(properties(3, 100, 3, GENEROUS_SLOT_TIMEOUT));
            recordFailures(limiter, CLIENT_IP, USERNAME, 3);

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isTrue();
        }

        @Test
        void shouldNotBlockPairOneFailureBelowConfiguredLimit() {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(properties(3, 100, 3, GENEROUS_SLOT_TIMEOUT));
            recordFailures(limiter, CLIENT_IP, USERNAME, 2);

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isFalse();
        }

        @Test
        void shouldNotBlockPairAtDefaultLimitWhenConfiguredLimitIsHigher() {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(properties(15, 100, 3, GENEROUS_SLOT_TIMEOUT));
            recordFailures(limiter, CLIENT_IP, USERNAME, 14);

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isFalse();
        }

        @Test
        void shouldBlockIpAtConfiguredLimitBelowDefault() {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(properties(10, 5, 3, GENEROUS_SLOT_TIMEOUT));
            recordFailuresOnDistinctUsernames(limiter, CLIENT_IP, 5);

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isTrue();
        }

        @Test
        void shouldNotBlockIpAtDefaultLimitWhenConfiguredLimitIsHigher() {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(properties(10, 150, 3, GENEROUS_SLOT_TIMEOUT));
            recordFailuresOnDistinctUsernames(limiter, CLIENT_IP, 149);

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isFalse();
        }

        @Test
        void shouldHonourConfiguredWindow() {
            LoginRateLimitProperties tenSecondWindow = new LoginRateLimitProperties(
                    10, 100, Duration.ofSeconds(10), 10_000, 10_000, 3, 30, GENEROUS_SLOT_TIMEOUT);
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(tenSecondWindow);
            recordFailures(limiter, CLIENT_IP, USERNAME, 10);
            clock.advance(Duration.ofSeconds(11));

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isFalse();
        }

        @Test
        void shouldHonourConfiguredNumberOfConcurrentChecks() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(properties(10, 100, 1, SHORT_SLOT_TIMEOUT));
            holdSlots(limiter, CLIENT_IP, 1);

            assertThatThrownBy(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "second"))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
        }
    }

    // ---------------------------------------------------------------- slots

    @Nested
    class PasswordCheckSlots {

        @Test
        void shouldReturnValueProducedByPasswordCheck() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();

            String result = limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "checked");

            assertThat(result).isEqualTo("checked");
        }

        @Test
        void shouldMakeFourthConcurrentCheckWaitForAFreeSlotInsteadOfFailing() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            HeldSlots held = holdSlots(limiter, CLIENT_IP, 3);
            AtomicBoolean fourthRan = new AtomicBoolean();
            AtomicReference<Thread> fourthThread = new AtomicReference<>();
            Future<String> fourth = executor.submit(() -> {
                fourthThread.set(Thread.currentThread());
                return limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> {
                    fourthRan.set(true);
                    return "fourth";
                });
            });
            awaitParked(fourthThread);
            boolean ranWhileAllSlotsWereBusy = fourthRan.get();
            boolean doneWhileAllSlotsWereBusy = fourth.isDone();

            held.releaseOne();

            assertThat(ranWhileAllSlotsWereBusy).isFalse();
            assertThat(doneWhileAllSlotsWereBusy).isFalse();
            assertThat(fourth.get(AWAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("fourth");
        }

        @Test
        void shouldRejectFourthConcurrentCheckWithTooManyAttemptsWhenSlotTimeoutExpires() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithShortSlotTimeout();
            holdSlots(limiter, CLIENT_IP, 3);

            assertThatThrownBy(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "fourth"))
                    .isInstanceOf(TooManyLoginAttemptsException.class)
                    .extracting(thrown -> ((TooManyLoginAttemptsException) thrown).code())
                    .isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        }

        @Test
        void shouldNotRunPasswordCheckWhoseSlotTimeoutExpired() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithShortSlotTimeout();
            holdSlots(limiter, CLIENT_IP, 3);
            AtomicBoolean fourthRan = new AtomicBoolean();

            catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> fourthRan.getAndSet(true)));

            assertThat(fourthRan).isFalse();
        }

        @Test
        void shouldPropagateExceptionThrownByPasswordCheck() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            IllegalStateException failure = new IllegalStateException("password check failed");

            Throwable thrown = catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> {
                throw failure;
            }));

            assertThat(thrown).isSameAs(failure);
        }

        @Test
        void shouldFreeSlotWhenPasswordCheckThrows() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithShortSlotTimeout();
            IntStream.range(0, 3).forEach(attempt -> catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> {
                throw new IllegalStateException("password check failed");
            })));
            holdSlots(limiter, CLIENT_IP, 3);

            assertThatThrownBy(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "fourth"))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
        }

        @Test
        void shouldNotShareSlotsBetweenDifferentIps() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithShortSlotTimeout();
            holdSlots(limiter, CLIENT_IP, 3);

            String result = limiter.runWithPasswordCheckSlot(OTHER_IP, () -> "other ip");

            assertThat(result).isEqualTo("other ip");
        }

        @Test
        void shouldKeepLimitingToThreeWhileOneSlotIsHeldDuringManyAcquireReleaseCycles() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithShortSlotTimeout();
            holdSlots(limiter, CLIENT_IP, 1);
            IntStream.range(0, 1_000).forEach(cycle -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> cycle));
            holdSlots(limiter, CLIENT_IP, 2);

            assertThatThrownBy(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "fourth"))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
        }

        @Test
        void shouldKeepLimitingToThreeAfterManyCyclesThatLeftTheIpIdle() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithShortSlotTimeout();
            IntStream.range(0, 1_000).forEach(cycle -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> cycle));
            holdSlots(limiter, CLIENT_IP, 3);

            assertThatThrownBy(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "fourth"))
                    .isInstanceOf(TooManyLoginAttemptsException.class);
        }

        @Test
        void shouldKeepLimitingToThreeWhenChecksArriveWhileOthersAreLeaving() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            AtomicInteger running = new AtomicInteger();
            AtomicInteger peak = new AtomicInteger();
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> checks = IntStream.range(0, 500)
                    .<Future<?>>mapToObj(check -> executor.submit(() -> {
                        awaitQuietly(start);
                        return limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> trackConcurrency(running, peak));
                    }))
                    .toList();

            start.countDown();
            for (Future<?> check : checks) {
                check.get(AWAIT_SECONDS, TimeUnit.SECONDS);
            }

            assertThat(peak.get()).isBetween(1, 3);
        }
    }

    // ---------------------------------------------------------------- concurrency of both layers

    private static Object trackConcurrency(AtomicInteger running, AtomicInteger peak) {
        int now = running.incrementAndGet();
        peak.accumulateAndGet(now, Math::max);
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        running.decrementAndGet();
        return null;
    }

    /**
     * The login flow as the spec describes it: check before the password check, check again once
     * the slot is obtained, and count the failure after the password check.
     */
    private static void attemptFailedLogin(
            InMemoryLoginAttemptRateLimiter limiter,
            String ip,
            String username,
            AtomicInteger acceptedFailures,
            AtomicInteger running,
            AtomicInteger peak) {
        if (limiter.isBlocked(ip, username)) {
            return;
        }
        limiter.runWithPasswordCheckSlot(ip, () -> {
            int now = running.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                if (!limiter.isBlocked(ip, username)) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                    limiter.recordFailure(ip, username);
                    acceptedFailures.incrementAndGet();
                }
                return null;
            } finally {
                running.decrementAndGet();
            }
        });
    }

    private record ConcurrentOutcome(int acceptedFailures, int peakConcurrentChecks) {}

    private ConcurrentOutcome runConcurrentFailedLogins(
            InMemoryLoginAttemptRateLimiter limiter, int attempts, boolean distinctUsernames) throws Exception {
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> logins = IntStream.range(0, attempts)
                .<Future<?>>mapToObj(attempt -> executor.submit(() -> {
                    awaitQuietly(start);
                    String username = distinctUsernames ? "spray_" + attempt : USERNAME;
                    attemptFailedLogin(limiter, CLIENT_IP, username, accepted, running, peak);
                    return null;
                }))
                .toList();
        start.countDown();
        for (Future<?> login : logins) {
            login.get(AWAIT_SECONDS * 3, TimeUnit.SECONDS);
        }
        return new ConcurrentOutcome(accepted.get(), peak.get());
    }

    @Nested
    class ConcurrentFailures {

        @RepeatedTest(5)
        void shouldAcceptAtLeastTenAndAtMostTwelveFailuresPerPairUnderConcurrency() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();

            ConcurrentOutcome outcome = runConcurrentFailedLogins(limiter, 60, false);

            assertThat(outcome.acceptedFailures()).isBetween(10, 12);
        }

        @RepeatedTest(5)
        void shouldNeverRunMoreThanThreePasswordChecksOfPairAtOnce() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();

            ConcurrentOutcome outcome = runConcurrentFailedLogins(limiter, 60, false);

            assertThat(outcome.peakConcurrentChecks()).isBetween(1, 3);
        }

        @RepeatedTest(3)
        void shouldAcceptAtLeastHundredAndAtMostHundredAndTwoFailuresPerIpUnderConcurrency() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();

            ConcurrentOutcome outcome = runConcurrentFailedLogins(limiter, 300, true);

            assertThat(outcome.acceptedFailures()).isBetween(100, 102);
        }

        @RepeatedTest(3)
        void shouldNeverRunMoreThanThreePasswordChecksOfIpAtOnceWhileSpraying() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();

            ConcurrentOutcome outcome = runConcurrentFailedLogins(limiter, 300, true);

            assertThat(outcome.peakConcurrentChecks()).isBetween(1, 3);
        }
    }

    // ---------------------------------------------------------------- round 5: malformed usernames (#62)

    private static final String UNICODE_VARIANT_OF_COZINHA = "cozİnha";

    /** Distinct malformed usernames: a space is never allowed. */
    private static void recordFailuresOnDistinctMalformedUsernames(InMemoryLoginAttemptRateLimiter limiter, String ip, int count) {
        IntStream.range(0, count).forEach(attempt -> limiter.recordFailure(ip, "user " + attempt));
    }

    @Nested
    class MalformedUsernamePair {

        @Test
        void shouldBlockAnyMalformedUsernameOfIpAfterTenFailuresOnDistinctMalformedUsernames() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailuresOnDistinctMalformedUsernames(limiter, CLIENT_IP, 10);

            assertThat(limiter.isBlocked(CLIENT_IP, "ab")).isTrue();
        }

        @Test
        void shouldNotBlockMalformedUsernamesAfterNineFailuresOnDistinctMalformedUsernames() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailuresOnDistinctMalformedUsernames(limiter, CLIENT_IP, 9);

            assertThat(limiter.isBlocked(CLIENT_IP, "ab")).isFalse();
        }

        @Test
        void shouldKeepWellFormedUsernameOfSameIpFreeWhenMalformedUsernamesAreBlocked() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailuresOnDistinctMalformedUsernames(limiter, CLIENT_IP, 10);

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isFalse();
        }

        @Test
        void shouldKeepMalformedUsernamesOfOtherIpFreeWhenMalformedUsernamesOfIpAreBlocked() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailuresOnDistinctMalformedUsernames(limiter, CLIENT_IP, 10);

            assertThat(limiter.isBlocked(OTHER_IP, "ab")).isFalse();
        }

        @Test
        void shouldCountMalformedUsernameFailuresTowardsIpLayer() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailuresOnDistinctUsernames(limiter, CLIENT_IP, 95);
            recordFailuresOnDistinctMalformedUsernames(limiter, CLIENT_IP, 5);

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isTrue();
        }

        @Test
        void shouldPutUnicodeVariantInTheSharedMalformedUsernamePair() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, UNICODE_VARIANT_OF_COZINHA, 10);

            assertThat(limiter.isBlocked(CLIENT_IP, "ab")).isTrue();
        }

        @Test
        void shouldTreatThirtyOneCharacterUsernamesAsTheSharedMalformedUsernamePair() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            IntStream.range(0, 10).forEach(attempt -> limiter.recordFailure(CLIENT_IP, "a".repeat(30) + attempt));

            assertThat(limiter.isBlocked(CLIENT_IP, "ab")).isTrue();
        }

        @Test
        void shouldKeepThirtyCharacterUsernamesInTheirOwnPairs() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, "a".repeat(30), 10);

            assertThat(limiter.isBlocked(CLIENT_IP, "b".repeat(30))).isFalse();
        }

        @Test
        void shouldUseSentinelKeyThatNoWellFormedUsernameCanEqual() {
            assertThat(User.isWellFormedUsername(InMemoryLoginAttemptRateLimiter.MALFORMED_USERNAME_KEY)).isFalse();
        }
    }

    // ---------------------------------------------------------------- round 5: window boundary (#66)

    @Nested
    class WindowBoundary {

        @Test
        void shouldReleasePairExactlySixtySecondsAfterTenthFailure() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, USERNAME, 10);
            clock.advance(Duration.ofSeconds(60));

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isFalse();
        }

        @Test
        void shouldKeepPairBlockedOneMillisecondBeforeSixtySeconds() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, USERNAME, 10);
            clock.advance(Duration.ofSeconds(60).minusMillis(1));

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isTrue();
        }

        @Test
        void shouldReleaseIpExactlySixtySecondsAfterHundredthFailure() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailuresOnDistinctUsernames(limiter, CLIENT_IP, 100);
            clock.advance(Duration.ofSeconds(60));

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isFalse();
        }

        @Test
        void shouldKeepIpBlockedOneMillisecondBeforeSixtySeconds() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailuresOnDistinctUsernames(limiter, CLIENT_IP, 100);
            clock.advance(Duration.ofSeconds(60).minusMillis(1));

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isTrue();
        }

        @Test
        void shouldReleasePairWhenOldestFailureReachesExactlySixtySeconds() {
            InMemoryLoginAttemptRateLimiter limiter = defaultLimiter();
            recordFailures(limiter, CLIENT_IP, USERNAME, 1);
            clock.advance(Duration.ofSeconds(1));
            recordFailures(limiter, CLIENT_IP, USERNAME, 9);
            clock.advance(Duration.ofSeconds(59));

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isFalse();
        }
    }

    // ---------------------------------------------------------------- round 5: slot wait outcomes (#64, #65, #68)

    private static final Duration INTERRUPTIBLE_SLOT_TIMEOUT = Duration.ofSeconds(2);

    private static LoginRateLimitProperties slotProperties(int maxWaitingChecks, Duration slotTimeout) {
        return new LoginRateLimitProperties(10, 100, WINDOW, 10_000, 10_000, 3, maxWaitingChecks, slotTimeout);
    }

    /** A password check started on a pool thread, with what happened to it. */
    private record Waiter(
            AtomicReference<Thread> thread,
            Future<Throwable> outcome,
            AtomicBoolean ran,
            AtomicBoolean interruptFlagAfterReturning) {

        Throwable awaitOutcome() throws Exception {
            return outcome.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private Waiter startCheck(InMemoryLoginAttemptRateLimiter limiter, String ip) {
        AtomicReference<Thread> thread = new AtomicReference<>();
        AtomicBoolean ran = new AtomicBoolean();
        AtomicBoolean interruptFlag = new AtomicBoolean();
        Future<Throwable> outcome = executor.submit(() -> {
            thread.set(Thread.currentThread());
            Throwable thrown = catchThrowable(() -> limiter.runWithPasswordCheckSlot(ip, () -> ran.getAndSet(true)));
            interruptFlag.set(Thread.currentThread().isInterrupted());
            return thrown;
        });
        return new Waiter(thread, outcome, ran, interruptFlag);
    }

    private Waiter startWaiter(InMemoryLoginAttemptRateLimiter limiter, String ip) {
        Waiter waiter = startCheck(limiter, ip);
        awaitParked(waiter.thread());
        return waiter;
    }

    private Waiter interruptedWaiter(InMemoryLoginAttemptRateLimiter limiter) throws Exception {
        Waiter waiter = startWaiter(limiter, CLIENT_IP);
        waiter.thread().get().interrupt();
        waiter.awaitOutcome();
        return waiter;
    }

    @Nested
    class InterruptedSlotWait {

        @Test
        void shouldRejectInterruptedWaitWithInterruptedReason() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(slotProperties(30, INTERRUPTIBLE_SLOT_TIMEOUT));
            holdSlots(limiter, CLIENT_IP, 3);

            Waiter waiter = interruptedWaiter(limiter);

            assertThat(waiter.awaitOutcome())
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::reason)
                    .isEqualTo(Reason.PASSWORD_CHECK_SLOT_WAIT_INTERRUPTED);
        }

        @Test
        void shouldRestoreInterruptFlagOfInterruptedWaiter() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(slotProperties(30, INTERRUPTIBLE_SLOT_TIMEOUT));
            holdSlots(limiter, CLIENT_IP, 3);

            Waiter waiter = interruptedWaiter(limiter);

            assertThat(waiter.interruptFlagAfterReturning()).isTrue();
        }

        @Test
        void shouldNotRunPasswordCheckOfInterruptedWaiter() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(slotProperties(30, INTERRUPTIBLE_SLOT_TIMEOUT));
            holdSlots(limiter, CLIENT_IP, 3);

            Waiter waiter = interruptedWaiter(limiter);

            assertThat(waiter.ran()).isFalse();
        }

        @Test
        void shouldLetThreeNewChecksInAfterInterruptedWaitAndRelease() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(slotProperties(30, INTERRUPTIBLE_SLOT_TIMEOUT));
            HeldSlots held = holdSlots(limiter, CLIENT_IP, 3);
            interruptedWaiter(limiter);
            held.releaseAll();

            Waiter first = startCheck(limiter, CLIENT_IP);
            Waiter second = startCheck(limiter, CLIENT_IP);
            Waiter third = startCheck(limiter, CLIENT_IP);

            assertThat(List.of(first.awaitOutcome() == null, second.awaitOutcome() == null, third.awaitOutcome() == null))
                    .containsOnly(true);
        }

        @Test
        void shouldNotGrantFourthSlotAfterInterruptedWait() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(slotProperties(30, INTERRUPTIBLE_SLOT_TIMEOUT));
            HeldSlots held = holdSlots(limiter, CLIENT_IP, 3);
            interruptedWaiter(limiter);
            held.releaseAll();
            holdSlots(limiter, CLIENT_IP, 3);

            Throwable fourth = catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "fourth"));

            assertThat(fourth)
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::reason)
                    .isEqualTo(Reason.PASSWORD_CHECK_SLOT_TIMEOUT);
        }

        @Test
        void shouldNotCountInterruptedWaitAsPairFailure() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(slotProperties(30, INTERRUPTIBLE_SLOT_TIMEOUT));
            recordFailures(limiter, CLIENT_IP, USERNAME, 9);
            holdSlots(limiter, CLIENT_IP, 3);

            interruptedWaiter(limiter);

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isFalse();
        }

        @Test
        void shouldNotCountInterruptedWaitAsIpFailure() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(slotProperties(30, INTERRUPTIBLE_SLOT_TIMEOUT));
            recordFailuresOnDistinctUsernames(limiter, CLIENT_IP, 99);
            holdSlots(limiter, CLIENT_IP, 3);

            interruptedWaiter(limiter);

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isFalse();
        }
    }

    @Nested
    class SlotTimeout {

        @Test
        void shouldRejectCheckWhoseWaitTimesOutWithSlotTimeoutReason() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithShortSlotTimeout();
            holdSlots(limiter, CLIENT_IP, 3);

            Throwable fourth = catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "fourth"));

            assertThat(fourth)
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::reason)
                    .isEqualTo(Reason.PASSWORD_CHECK_SLOT_TIMEOUT);
        }

        @Test
        void shouldNotCountSlotTimeoutAsPairFailure() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithShortSlotTimeout();
            recordFailures(limiter, CLIENT_IP, USERNAME, 9);
            holdSlots(limiter, CLIENT_IP, 3);

            catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "fourth"));

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isFalse();
        }

        @Test
        void shouldNotCountSlotTimeoutAsIpFailure() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithShortSlotTimeout();
            recordFailuresOnDistinctUsernames(limiter, CLIENT_IP, 99);
            holdSlots(limiter, CLIENT_IP, 3);

            catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "fourth"));

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isFalse();
        }
    }

    @Nested
    class WaitingLimit {

        private static final int WAITING_LIMIT = 2;

        private InMemoryLoginAttemptRateLimiter limiterWithSmallWaitingLimit() {
            return limiterWith(slotProperties(WAITING_LIMIT, GENEROUS_SLOT_TIMEOUT));
        }

        @Test
        void shouldLetCheckWaitWhileWaitingChecksAreBelowTheLimit() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithSmallWaitingLimit();
            holdSlots(limiter, CLIENT_IP, 3);
            startWaiter(limiter, CLIENT_IP);

            Waiter second = startWaiter(limiter, CLIENT_IP);

            assertThat(second.outcome().isDone()).isFalse();
        }

        @Test
        void shouldRejectCheckBeyondWaitingLimitWithWaitingLimitReason() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithSmallWaitingLimit();
            holdSlots(limiter, CLIENT_IP, 3);
            startWaiter(limiter, CLIENT_IP);
            startWaiter(limiter, CLIENT_IP);

            Throwable beyondLimit = catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "beyond"));

            assertThat(beyondLimit)
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::reason)
                    .isEqualTo(Reason.WAITING_LIMIT_REACHED);
        }

        @Test
        void shouldRejectCheckBeyondWaitingLimitWellBeforeTheSlotTimeout() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithSmallWaitingLimit();
            holdSlots(limiter, CLIENT_IP, 3);
            startWaiter(limiter, CLIENT_IP);
            startWaiter(limiter, CLIENT_IP);

            long started = System.nanoTime();
            catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "beyond"));
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertThat(elapsed).isLessThan(GENEROUS_SLOT_TIMEOUT.dividedBy(10));
        }

        @Test
        void shouldNotRunCheckRejectedByWaitingLimit() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithSmallWaitingLimit();
            holdSlots(limiter, CLIENT_IP, 3);
            startWaiter(limiter, CLIENT_IP);
            startWaiter(limiter, CLIENT_IP);
            AtomicBoolean ran = new AtomicBoolean();

            catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> ran.getAndSet(true)));

            assertThat(ran).isFalse();
        }

        @Test
        void shouldApplyWaitingLimitPerIp() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithSmallWaitingLimit();
            holdSlots(limiter, CLIENT_IP, 3);
            startWaiter(limiter, CLIENT_IP);
            startWaiter(limiter, CLIENT_IP);

            String result = limiter.runWithPasswordCheckSlot(OTHER_IP, () -> "other ip");

            assertThat(result).isEqualTo("other ip");
        }

        @Test
        void shouldNotCountWaitingLimitRejectionAsPairFailure() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithSmallWaitingLimit();
            recordFailures(limiter, CLIENT_IP, USERNAME, 9);
            holdSlots(limiter, CLIENT_IP, 3);
            startWaiter(limiter, CLIENT_IP);
            startWaiter(limiter, CLIENT_IP);

            catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "beyond"));

            assertThat(limiter.isBlocked(CLIENT_IP, USERNAME)).isFalse();
        }

        @Test
        void shouldNotCountWaitingLimitRejectionAsIpFailure() throws InterruptedException {
            InMemoryLoginAttemptRateLimiter limiter = limiterWithSmallWaitingLimit();
            recordFailuresOnDistinctUsernames(limiter, CLIENT_IP, 99);
            holdSlots(limiter, CLIENT_IP, 3);
            startWaiter(limiter, CLIENT_IP);
            startWaiter(limiter, CLIENT_IP);

            catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "beyond"));

            assertThat(limiter.isBlocked(CLIENT_IP, "never_used")).isFalse();
        }
    }

    @Nested
    class SlotsAfterEverythingFinishes {

        /** 3 held, 2 waiting, 1 rejected by the waiting limit; then everything is released and finishes. */
        private void runFullCycle(InMemoryLoginAttemptRateLimiter limiter) throws Exception {
            HeldSlots held = holdSlots(limiter, CLIENT_IP, 3);
            Waiter first = startWaiter(limiter, CLIENT_IP);
            Waiter second = startWaiter(limiter, CLIENT_IP);
            catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "beyond"));
            held.releaseAll();
            first.awaitOutcome();
            second.awaitOutcome();
        }

        @Test
        void shouldAcceptThreeHeldChecksAgainAfterWaitersAndRejectionFinish() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(slotProperties(2, INTERRUPTIBLE_SLOT_TIMEOUT));
            runFullCycle(limiter);
            holdSlots(limiter, CLIENT_IP, 3);

            Throwable fourth = catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "fourth"));

            assertThat(fourth)
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::reason)
                    .isEqualTo(Reason.PASSWORD_CHECK_SLOT_TIMEOUT);
        }

        @Test
        void shouldLetTwoChecksWaitAgainAfterWaitersAndRejectionFinish() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(slotProperties(2, GENEROUS_SLOT_TIMEOUT));
            runFullCycle(limiter);
            holdSlots(limiter, CLIENT_IP, 3);
            startWaiter(limiter, CLIENT_IP);

            Waiter second = startWaiter(limiter, CLIENT_IP);

            assertThat(second.outcome().isDone()).isFalse();
        }

        @Test
        void shouldApplyWaitingLimitAgainAfterWaitersAndRejectionFinish() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(slotProperties(2, GENEROUS_SLOT_TIMEOUT));
            runFullCycle(limiter);
            holdSlots(limiter, CLIENT_IP, 3);
            startWaiter(limiter, CLIENT_IP);
            startWaiter(limiter, CLIENT_IP);

            Throwable beyondLimit = catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "beyond"));

            assertThat(beyondLimit)
                    .asInstanceOf(type(TooManyLoginAttemptsException.class))
                    .extracting(TooManyLoginAttemptsException::reason)
                    .isEqualTo(Reason.WAITING_LIMIT_REACHED);
        }

        @Test
        void shouldKeepWaitingSeatsAvailableAfterWaitersTimedOut() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(slotProperties(2, SHORT_SLOT_TIMEOUT));
            holdSlots(limiter, CLIENT_IP, 3);
            startCheck(limiter, CLIENT_IP).awaitOutcome();
            startCheck(limiter, CLIENT_IP).awaitOutcome();

            Waiter first = startCheck(limiter, CLIENT_IP);
            Waiter second = startCheck(limiter, CLIENT_IP);

            assertThat(List.of(reasonOf(first), reasonOf(second)))
                    .containsOnly(Reason.PASSWORD_CHECK_SLOT_TIMEOUT);
        }

        @Test
        void shouldNotGrantFourthSlotAfterWaitersTimedOut() throws Exception {
            InMemoryLoginAttemptRateLimiter limiter = limiterWith(slotProperties(2, SHORT_SLOT_TIMEOUT));
            HeldSlots held = holdSlots(limiter, CLIENT_IP, 3);
            startCheck(limiter, CLIENT_IP).awaitOutcome();
            startCheck(limiter, CLIENT_IP).awaitOutcome();
            held.releaseAll();
            holdSlots(limiter, CLIENT_IP, 3);

            Throwable fourth = catchThrowable(() -> limiter.runWithPasswordCheckSlot(CLIENT_IP, () -> "fourth"));

            assertThat(fourth).isInstanceOf(TooManyLoginAttemptsException.class);
        }

        private Reason reasonOf(Waiter waiter) throws Exception {
            return ((TooManyLoginAttemptsException) waiter.awaitOutcome()).reason();
        }
    }
}
