package br.com.castel.identity.application;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Real low-cost BCrypt that records, for every password comparison, where it ran (inside a slot,
 * inside a transaction, holding a row lock) and the peak of simultaneous comparisons.
 *
 * <p>Two different counts, on purpose:
 *
 * <ul>
 *   <li>{@link #comparisons()}: every call to {@code matches}, even one the delegate gives up on.
 *   <li>{@link #hashesComputed()}: only calls that really reached {@code BCrypt.checkpw}. In Spring
 *       Security 7 {@code matches} returns {@code false} without hashing when the raw password or
 *       the stored hash is empty, and {@code BCryptPasswordEncoder.matchesNonNull} returns
 *       {@code false} without hashing when the stored hash is not BCrypt-shaped. The count is taken
 *       inside {@code matchesNonNull}, after the same shape check, so it only moves when a hash is
 *       computed. The slot, transaction and row-lock probes are also taken there.
 * </ul>
 *
 * <p>Optionally gates comparisons of one raw password: they park until {@link #open()}, so a test
 * can observe the system while they are all "in BCrypt".
 */
final class ObservingPasswordEncoder implements PasswordEncoder {

    private static final long AWAIT_SECONDS = 10;
    private static final BooleanSupplier NEVER = () -> false;
    /** Same shape check as {@code BCryptPasswordEncoder} 7.1.1 does before {@code BCrypt.checkpw}. */
    private static final Pattern BCRYPT_SHAPE = Pattern.compile("\\A\\$2(a|y|b)?\\$(\\d\\d)\\$[./0-9A-Za-z]{53}");

    private final PasswordEncoder delegate = new HashCountingBCryptPasswordEncoder();
    private final BooleanSupplier insideSlot;
    private final BooleanSupplier insideTransaction;
    private final BooleanSupplier holdingRowLock;
    private final String gatedRawPassword;
    private final CountDownLatch gatedHolders;
    private final CountDownLatch gate = new CountDownLatch(1);
    private final AtomicInteger comparisons = new AtomicInteger();
    private final AtomicInteger hashesComputed = new AtomicInteger();
    private final List<String> hashedCandidates = new CopyOnWriteArrayList<>();
    private final AtomicInteger comparisonsInsideSlot = new AtomicInteger();
    private final AtomicInteger comparisonsInsideTransaction = new AtomicInteger();
    private final AtomicInteger comparisonsHoldingRowLock = new AtomicInteger();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();

    private ObservingPasswordEncoder(
            BooleanSupplier insideSlot,
            BooleanSupplier insideTransaction,
            BooleanSupplier holdingRowLock,
            String gatedRawPassword,
            int gatedHolders) {
        this.insideSlot = insideSlot;
        this.insideTransaction = insideTransaction;
        this.holdingRowLock = holdingRowLock;
        this.gatedRawPassword = gatedRawPassword;
        this.gatedHolders = new CountDownLatch(gatedHolders);
    }

    static ObservingPasswordEncoder observing(
            BooleanSupplier insideSlot, BooleanSupplier insideTransaction, BooleanSupplier holdingRowLock) {
        return new ObservingPasswordEncoder(insideSlot, insideTransaction, holdingRowLock, null, 0);
    }

    static ObservingPasswordEncoder observingSlots(BooleanSupplier insideSlot) {
        return observing(insideSlot, NEVER, NEVER);
    }

    static ObservingPasswordEncoder unobserved() {
        return observing(NEVER, NEVER, NEVER);
    }

    /** Same probes, but comparisons of {@code rawPassword} park until {@link #open()}. */
    ObservingPasswordEncoder gating(String rawPassword, int expectedHolders) {
        return new ObservingPasswordEncoder(insideSlot, insideTransaction, holdingRowLock, rawPassword, expectedHolders);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        comparisons.incrementAndGet();
        peak.accumulateAndGet(running.incrementAndGet(), Math::max);
        try {
            parkWhenGated(rawPassword);
            return delegate.matches(rawPassword, encodedPassword);
        } finally {
            running.decrementAndGet();
        }
    }

    /** Real BCrypt (cost 4) that records a hash only when {@code BCrypt.checkpw} is about to run. */
    private final class HashCountingBCryptPasswordEncoder extends BCryptPasswordEncoder {

        HashCountingBCryptPasswordEncoder() {
            super(4);
        }

        @Override
        protected boolean matchesNonNull(String rawPassword, String encodedPassword) {
            if (BCRYPT_SHAPE.matcher(encodedPassword).matches()) {
                hashesComputed.incrementAndGet();
                hashedCandidates.add(rawPassword);
                count(insideSlot, comparisonsInsideSlot);
                count(insideTransaction, comparisonsInsideTransaction);
                count(holdingRowLock, comparisonsHoldingRowLock);
            }
            return super.matchesNonNull(rawPassword, encodedPassword);
        }
    }

    private static void count(BooleanSupplier probe, AtomicInteger counter) {
        if (probe.getAsBoolean()) {
            counter.incrementAndGet();
        }
    }

    private void parkWhenGated(CharSequence rawPassword) {
        if (gatedRawPassword == null || rawPassword == null || !gatedRawPassword.contentEquals(rawPassword)) {
            return;
        }
        gatedHolders.countDown();
        try {
            gate.await(AWAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    boolean awaitGatedHolders() throws InterruptedException {
        return gatedHolders.await(AWAIT_SECONDS, TimeUnit.SECONDS);
    }

    void open() {
        gate.countDown();
    }

    /** Every call to {@code matches}, including the ones the delegate returns without hashing. */
    int comparisons() {
        return comparisons.get();
    }

    /** Calls that really computed a BCrypt hash. */
    int hashesComputed() {
        return hashesComputed.get();
    }

    /** Raw candidates that were actually hashed, in order. */
    List<String> hashedCandidates() {
        return List.copyOf(hashedCandidates);
    }

    /** Computed hashes that ran inside a password check slot. */
    int comparisonsInsideSlot() {
        return comparisonsInsideSlot.get();
    }

    /** Computed hashes that ran inside a transaction. */
    int comparisonsInsideTransaction() {
        return comparisonsInsideTransaction.get();
    }

    /** Computed hashes that ran while holding a row lock. */
    int comparisonsHoldingRowLock() {
        return comparisonsHoldingRowLock.get();
    }

    int peakConcurrentComparisons() {
        return peak.get();
    }
}
