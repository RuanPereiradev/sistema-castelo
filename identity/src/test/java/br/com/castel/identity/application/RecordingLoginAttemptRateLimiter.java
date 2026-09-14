package br.com.castel.identity.application;

import br.com.castel.identity.infra.InMemoryLoginAttemptRateLimiter;
import br.com.castel.identity.infra.LoginRateLimitProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Decorator over the real in-memory limiter that records what the service asks of the port: how
 * many failures were recorded, and whether the current thread is inside a password check slot.
 */
final class RecordingLoginAttemptRateLimiter implements LoginAttemptRateLimiter {

    static final long AWAIT_SECONDS = 10;

    private final LoginAttemptRateLimiter delegate;
    private final ThreadLocal<Boolean> insideSlot = ThreadLocal.withInitial(() -> false);
    private final AtomicInteger recordedFailures = new AtomicInteger();
    private final List<CountDownLatch> heldSlotReleases = new ArrayList<>();

    private RecordingLoginAttemptRateLimiter(LoginAttemptRateLimiter delegate) {
        this.delegate = delegate;
    }

    /** Default limits of the spec (10 per pair, 100 per IP, 1 min, 3 slots) with the given slot settings. */
    static RecordingLoginAttemptRateLimiter inMemory(int maxWaitingChecksPerIp, Duration slotTimeout, Clock clock) {
        LoginRateLimitProperties properties = new LoginRateLimitProperties(
                10, 100, Duration.ofMinutes(1), 10_000, 10_000, 3, maxWaitingChecksPerIp, slotTimeout);
        return new RecordingLoginAttemptRateLimiter(new InMemoryLoginAttemptRateLimiter(
                InMemoryLoginAttemptRateLimiter.newFailuresCache(10_000, properties.window(), clock),
                InMemoryLoginAttemptRateLimiter.newFailuresCache(10_000, properties.window(), clock),
                properties,
                clock));
    }

    @Override
    public boolean isBlocked(String ip, String username) {
        return delegate.isBlocked(ip, username);
    }

    @Override
    public void recordFailure(String ip, String username) {
        recordedFailures.incrementAndGet();
        delegate.recordFailure(ip, username);
    }

    @Override
    public <T> T runWithPasswordCheckSlot(String ip, Supplier<T> passwordCheck) {
        return delegate.runWithPasswordCheckSlot(ip, () -> {
            insideSlot.set(true);
            try {
                return passwordCheck.get();
            } finally {
                insideSlot.set(false);
            }
        });
    }

    boolean isCurrentThreadInsideSlot() {
        return insideSlot.get();
    }

    int recordedFailures() {
        return recordedFailures.get();
    }

    /** Records failures straight on the real limiter: no service, no audit log, not counted here. */
    void recordFailuresWithoutLogin(String ip, String username, int count) {
        IntStream.range(0, count).forEach(attempt -> delegate.recordFailure(ip, username));
    }

    void recordFailuresOnDistinctUsernamesWithoutLogin(String ip, int count) {
        IntStream.range(0, count).forEach(attempt -> delegate.recordFailure(ip, "spray_" + attempt));
    }

    /** Parks {@code count} password checks inside slots of {@code ip} until {@link #releaseHeldSlots()}. */
    void holdSlots(String ip, int count, ExecutorService executor) throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(count);
        CountDownLatch release = new CountDownLatch(1);
        synchronized (heldSlotReleases) {
            heldSlotReleases.add(release);
        }
        IntStream.range(0, count).forEach(slot -> executor.submit(() -> delegate.runWithPasswordCheckSlot(ip, () -> {
            entered.countDown();
            awaitQuietly(release);
            return null;
        })));
        if (!entered.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("password check slots could not be held");
        }
    }

    void releaseHeldSlots() {
        synchronized (heldSlotReleases) {
            heldSlotReleases.forEach(CountDownLatch::countDown);
            heldSlotReleases.clear();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(AWAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Waits until the thread is parked (waiting for a slot), failing after a generous deadline. */
    static void awaitWaiting(AtomicReference<Thread> threadReference) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (!isWaiting(threadReference.get()) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        if (!isWaiting(threadReference.get())) {
            throw new IllegalStateException("thread did not start waiting for a password check slot");
        }
    }

    private static boolean isWaiting(Thread thread) {
        return thread != null
                && (thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING);
    }
}
