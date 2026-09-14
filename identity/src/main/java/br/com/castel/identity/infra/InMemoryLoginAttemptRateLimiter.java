package br.com.castel.identity.infra;

import br.com.castel.identity.application.LoginAttemptRateLimiter;
import br.com.castel.identity.application.TooManyLoginAttemptsException;
import br.com.castel.identity.application.TooManyLoginAttemptsException.Reason;
import br.com.castel.identity.domain.User;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link LoginAttemptRateLimiter}: sliding-window failure counters in two Caffeine caches,
 * and one fair semaphore per client IP for the concurrent password checks.
 *
 * <p><b>Counters.</b> Each key keeps the instants of its most recent failures, at most as many as
 * its limit: a key is blocked while that many failures sit inside the window, and keeping only the
 * most recent ones gives exactly that answer. Memory is bounded by the caches' maximum size, and a
 * key untouched for a whole window expires. Every access to a key's deque happens inside
 * {@code compute}, which serializes it per key, and the clock is read there too.
 *
 * <p><b>Pair key.</b> The username of the key is normalized by {@link User#normalizedUsername}, the
 * same normalization the login looks the user up with, so every input that can reach a user shares
 * that user's key. A username that is not well formed ({@link User#isWellFormedUsername}) cannot
 * belong to any user; all of them share one {@link #MALFORMED_USERNAME_KEY sentinel} per IP. So a
 * key is at most 30 characters or the sentinel, and arbitrary client text never becomes a key.
 *
 * <p><b>Semaphores.</b> They live in a plain {@link ConcurrentHashMap}, never in an evicting cache:
 * evicting the semaphore of an IP while some of its permits are held would let the next request
 * create a fresh one and run more password checks than allowed. Instead, each entry counts the
 * requests currently using it (waiting or holding a permit), incremented and decremented inside
 * {@code compute}, and the entry is removed only when that count drops to zero. So an entry exists
 * exactly while some request uses it, and the map holds at most one entry per in-flight login,
 * bounded by the server's request threads.
 *
 * <p><b>Waiting limit.</b> The same count caps the requests of an IP: at most the concurrent checks
 * plus the waiting limit. A request arriving beyond it is rejected at once, inside the same
 * {@code compute}, and never touches the semaphore nor the count. The count also includes a
 * request that already gave its permit back and is about to leave, so for that instant the cap is
 * conservative by one: it may reject a request that would have found a free permit.
 */
@Component
public class InMemoryLoginAttemptRateLimiter implements LoginAttemptRateLimiter {

    /**
     * Username of the pair key shared by every username that is not well formed. It holds
     * characters outside {@code [a-z0-9._]}, so it can never be the key of a real username.
     */
    static final String MALFORMED_USERNAME_KEY = "<malformed>";

    private final Clock clock;
    private final Duration window;
    private final int maxFailuresPerUsername;
    private final int maxFailuresPerIp;
    private final int maxConcurrentPasswordChecksPerIp;
    private final long maxRequestsUsingSlotsPerIp;
    private final Duration passwordCheckSlotTimeout;
    private final Cache<IpAndUsername, Deque<Instant>> failuresByPair;
    private final Cache<String, Deque<Instant>> failuresByIp;
    private final ConcurrentMap<String, PasswordCheckSlots> passwordCheckSlotsByIp = new ConcurrentHashMap<>();

    /**
     * @param failuresByPair failures per pair (client IP, username), built by {@link #newFailuresCache};
     *        a bean of its own so an integration test can empty it
     * @param failuresByIp failures per client IP, built by {@link #newFailuresCache}; same reason
     */
    public InMemoryLoginAttemptRateLimiter(
            @Qualifier("loginFailuresByPair") Cache<IpAndUsername, Deque<Instant>> failuresByPair,
            @Qualifier("loginFailuresByIp") Cache<String, Deque<Instant>> failuresByIp,
            LoginRateLimitProperties properties,
            Clock clock) {
        this.failuresByPair = failuresByPair;
        this.failuresByIp = failuresByIp;
        this.clock = clock;
        this.window = properties.window();
        this.maxFailuresPerUsername = properties.maxFailuresPerUsername();
        this.maxFailuresPerIp = properties.maxFailuresPerIp();
        this.maxConcurrentPasswordChecksPerIp = properties.maxConcurrentPasswordChecksPerIp();
        this.maxRequestsUsingSlotsPerIp =
                (long) properties.maxConcurrentPasswordChecksPerIp() + properties.maxWaitingPasswordChecksPerIp();
        this.passwordCheckSlotTimeout = properties.passwordCheckSlotTimeout();
    }

    /** Builds a bounded, clock-driven failure store of {@code maximumSize} keys expiring after {@code window}. */
    public static <K> Cache<K, Deque<Instant>> newFailuresCache(long maximumSize, Duration window, Clock clock) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(window)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    @Override
    public boolean isBlocked(String clientIp, String username) {
        return hasReachedLimit(failuresByPair, pairKey(clientIp, username), maxFailuresPerUsername)
                || hasReachedLimit(failuresByIp, clientIp, maxFailuresPerIp);
    }

    @Override
    public void recordFailure(String clientIp, String username) {
        addFailure(failuresByPair, pairKey(clientIp, username), maxFailuresPerUsername);
        addFailure(failuresByIp, clientIp, maxFailuresPerIp);
    }

    @Override
    public <T> T runWithPasswordCheckSlot(String clientIp, Supplier<T> passwordCheck) {
        PasswordCheckSlots slots = enter(clientIp);
        try {
            acquire(slots.semaphore);
            try {
                return passwordCheck.get();
            } finally {
                slots.semaphore.release();
            }
        } finally {
            passwordCheckSlotsByIp.computeIfPresent(clientIp, (ip, inUse) -> --inUse.users == 0 ? null : inUse);
        }
    }

    /**
     * Counts the request as a user of the IP's slots, or rejects it at once when the IP already has
     * as many requests using its slots as allowed. A rejected request leaves the map as it found
     * it: a new entry always admits its first request, and an existing one is returned untouched.
     */
    private PasswordCheckSlots enter(String clientIp) {
        AtomicBoolean admitted = new AtomicBoolean(false);
        PasswordCheckSlots slots = passwordCheckSlotsByIp.compute(clientIp, (ip, existing) -> {
            PasswordCheckSlots inUse = existing == null ? new PasswordCheckSlots(maxConcurrentPasswordChecksPerIp) : existing;
            if (inUse.users < maxRequestsUsingSlotsPerIp) {
                inUse.users++;
                admitted.set(true);
            }
            return inUse;
        });
        if (!admitted.get()) {
            throw new TooManyLoginAttemptsException(Reason.WAITING_LIMIT_REACHED);
        }
        return slots;
    }

    private void acquire(Semaphore semaphore) {
        try {
            if (!semaphore.tryAcquire(passwordCheckSlotTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new TooManyLoginAttemptsException(Reason.PASSWORD_CHECK_SLOT_TIMEOUT);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TooManyLoginAttemptsException(Reason.PASSWORD_CHECK_SLOT_WAIT_INTERRUPTED);
        }
    }

    private <K> boolean hasReachedLimit(Cache<K, Deque<Instant>> failures, K key, int limit) {
        AtomicBoolean reached = new AtomicBoolean(false);
        failures.asMap().computeIfPresent(key, (ignored, instants) -> {
            discardOutsideWindow(instants, clock.instant());
            reached.set(instants.size() >= limit);
            return instants.isEmpty() ? null : instants;
        });
        return reached.get();
    }

    private <K> void addFailure(Cache<K, Deque<Instant>> failures, K key, int limit) {
        failures.asMap().compute(key, (ignored, existing) -> {
            Instant now = clock.instant();
            Deque<Instant> instants = existing == null ? new ArrayDeque<>(limit) : existing;
            discardOutsideWindow(instants, now);
            if (instants.size() >= limit) {
                instants.pollFirst();
            }
            instants.addLast(now);
            return instants;
        });
    }

    /** A failure exactly {@code window} old is already outside the window ({@code >=}). */
    private void discardOutsideWindow(Deque<Instant> instants, Instant now) {
        while (!instants.isEmpty() && Duration.between(instants.peekFirst(), now).compareTo(window) >= 0) {
            instants.pollFirst();
        }
    }

    private static IpAndUsername pairKey(String clientIp, String username) {
        String keyUsername = User.isWellFormedUsername(username) ? User.normalizedUsername(username) : MALFORMED_USERNAME_KEY;
        return new IpAndUsername(clientIp, keyUsername);
    }

    /** Key of the per-pair failure counter: a normalized well-formed username, or the sentinel. */
    record IpAndUsername(String clientIp, String username) {
    }

    /** The semaphore of one client IP, and how many requests are using it right now. */
    private static final class PasswordCheckSlots {

        private final Semaphore semaphore;

        /** Read and written only inside {@code compute} for this IP's key. */
        private int users;

        private PasswordCheckSlots(int permits) {
            this.semaphore = new Semaphore(permits, true);
        }
    }
}
