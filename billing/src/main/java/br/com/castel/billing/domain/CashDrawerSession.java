package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.AuditedEntity;
import br.com.castel.sharedkernel.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * A shift of the cash drawer: opened with a float, moved by drops and supplies, fed by the
 * {@code CASH} payments linked to it, and closed with the count of the notes in the drawer.
 *
 * <p>The {@linkplain #expectedAmount(Money) expected amount} is what the drawer should hold: the float,
 * plus the confirmed cash payments, plus the supplies, minus the drops. The payments belong to the
 * folios, so the use case sums them and hands the total in. Closing freezes the expected amount: a
 * refund after the closing does not rewrite a conference already made (decision C8 of task 2.4). The
 * {@linkplain #difference() difference} is the count minus the frozen amount, calculated, never
 * stored.
 *
 * <p>A difference never blocks the closing (decision C6); it only requires a note. A drop above the
 * expected amount is accepted (decision C7): the shortfall shows at the closing, which is blind for
 * whoever is not an {@code ADMIN} (decision C4, {@link #revealsExpectedAmountTo}).
 *
 * <p>One open session per property is a rule about the set of sessions, checked by the use case and
 * held by {@code idx_cash_session_open} (decision C1).
 */
@Entity
@Table(name = "cash_drawer_session")
public class CashDrawerSession extends AuditedEntity {

    /** Maximum length of the closing note, after trimming. */
    public static final int MAXIMUM_CLOSING_NOTE_LENGTH = 500;

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private CashDrawerSessionStatus status;

    @Column(name = "opened_by", nullable = false)
    private UUID openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opening_float", nullable = false)
    private Money openingFloat;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "expected_amount")
    private Money expectedAmount;

    @Column(name = "counted_amount")
    private Money countedAmount;

    @Column(name = "closing_note")
    private String closingNote;

    /*
     * Fetched by subselect, so the SELECT ... FOR UPDATE that locks the session does not join the
     * movements. Ordered as they were written: by creation time, then by id, a version 7 UUID.
     */
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "cash_drawer_session_id", nullable = false, updatable = false)
    @OrderBy("createdAt, id")
    @Fetch(FetchMode.SUBSELECT)
    private List<CashMovement> movements = new ArrayList<>();

    protected CashDrawerSession() {
        // for JPA
    }

    private CashDrawerSession(UUID propertyId, Money openingFloat, UUID openedBy, Instant openedAt) {
        this.id = CashDrawerSessionId.newId().value();
        this.propertyId = propertyId;
        this.status = CashDrawerSessionStatus.OPEN;
        this.openingFloat = openingFloat;
        this.openedBy = openedBy;
        this.openedAt = openedAt;
    }

    /**
     * A session born open. A float of zero is valid.
     *
     * @throws InvalidOpeningFloatException if the float is missing or below zero
     */
    public static CashDrawerSession open(UUID propertyId, Money openingFloat, UUID openedBy, Instant openedAt) {
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(openedBy, "openedBy");
        Objects.requireNonNull(openedAt, "openedAt");
        if (openingFloat == null || openingFloat.isNegative()) {
            throw new InvalidOpeningFloatException("The opening float must not be below zero");
        }
        return new CashDrawerSession(propertyId, openingFloat, openedBy, openedAt);
    }

    // ------------------------------------------------------------------ movements

    /**
     * Takes cash out of the drawer. Accepted above the expected amount (decision C7 of task 2.4).
     *
     * @return the new drop, or the one already registered under this key
     * @throws InvalidIdempotencyKeyException if the key is blank or longer than 100 characters
     * @throws IdempotencyKeyReusedException if this session holds the key with another type or amount
     * @throws CashDrawerSessionClosedException if the session is closed
     * @throws InvalidCashMovementAmountException if the amount is missing or not greater than zero
     * @throws InvalidCashMovementReasonException if the reason is blank or longer than 500
     */
    public CashMovement drop(Money amount, String reason, String idempotencyKey) {
        return move(CashMovementType.CASH_DROP, amount, reason, idempotencyKey);
    }

    /**
     * Puts cash into the drawer. Same rules and order as {@link #drop}.
     *
     * @return the new supply, or the one already registered under this key
     */
    public CashMovement supply(Money amount, String reason, String idempotencyKey) {
        return move(CashMovementType.CASH_SUPPLY, amount, reason, idempotencyKey);
    }

    /**
     * A retry is recognised before any other rule, even on a closed session (decision C9 of task
     * 2.4): a key this session already holds with the same type and amount answers the movement
     * already registered, and writes nothing.
     */
    private CashMovement move(CashMovementType type, Money amount, String reason, String idempotencyKey) {
        String key = Payment.requireValidIdempotencyKey(idempotencyKey);
        Optional<CashMovement> earlier = movementWithKey(key);
        if (earlier.isPresent()) {
            return replay(earlier.get(), type, amount);
        }
        requireOpen();
        if (amount == null || !amount.isPositive()) {
            throw new InvalidCashMovementAmountException("A cash movement must be greater than zero");
        }
        String validReason = BoundedText.require(
                reason, CashMovement.MAXIMUM_REASON_LENGTH, "cash movement reason",
                InvalidCashMovementReasonException::new);
        CashMovement movement = CashMovement.of(type, amount, validReason, key);
        movements.add(movement);
        return movement;
    }

    private static CashMovement replay(CashMovement earlier, CashMovementType type, Money amount) {
        if (!earlier.matches(type, amount)) {
            throw new IdempotencyKeyReusedException(
                    "The idempotency key was used by a cash movement of another type or amount");
        }
        return earlier;
    }

    /** The movement this session registered under the key, trimmed; how a retry is recognised. */
    public Optional<CashMovement> movementWithKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        String key = idempotencyKey.trim();
        return movements.stream().filter(movement -> movement.hasIdempotencyKey(key)).findFirst();
    }

    // ------------------------------------------------------------------ closing

    /**
     * Closes the session with the count of the drawer, freezing the expected amount. Never refused for
     * a difference (decision C6 of task 2.4); a difference only requires a note.
     *
     * @param cashPayments the confirmed cash payments linked to this session, summed after locking it
     * @param closedByAdmin whether whoever closes is an {@code ADMIN}, who closes any session
     * @throws CashDrawerSessionClosedException if the session is already closed
     * @throws CashDrawerSessionNotOwnedException if whoever closes neither opened it nor is an {@code ADMIN}
     * @throws InvalidCountedAmountException if the count is missing or below zero
     * @throws CashClosingNoteRequiredException if there is a difference and no note, or the note is
     *     longer than 500 characters
     */
    public void close(
            Money countedAmount,
            Money cashPayments,
            String note,
            UUID closedBy,
            boolean closedByAdmin,
            Instant closedAt) {
        Objects.requireNonNull(cashPayments, "cashPayments");
        Objects.requireNonNull(closedBy, "closedBy");
        Objects.requireNonNull(closedAt, "closedAt");
        requireOpen();
        if (!closedByAdmin && !closedBy.equals(openedBy)) {
            throw new CashDrawerSessionNotOwnedException(
                    "Cash drawer session " + id + " is closed by whoever opened it or by an ADMIN");
        }
        if (countedAmount == null || countedAmount.isNegative()) {
            throw new InvalidCountedAmountException("The counted amount must not be below zero");
        }
        Money expected = expectedAmount(cashPayments);
        this.closingNote = validClosingNote(note, countedAmount.minus(expected));
        this.expectedAmount = expected;
        this.countedAmount = countedAmount;
        this.closedBy = closedBy;
        this.closedAt = closedAt;
        this.status = CashDrawerSessionStatus.CLOSED;
    }

    /** Required with a difference; optional without one, where a blank note is no note at all. */
    private static String validClosingNote(String note, Money difference) {
        if (difference.isZero() && (note == null || note.isBlank())) {
            return null;
        }
        return BoundedText.require(
                note, MAXIMUM_CLOSING_NOTE_LENGTH, "closing note", CashClosingNoteRequiredException::new);
    }

    // ------------------------------------------------------------------ totals

    /**
     * What the drawer should hold: the float, plus the supplies and minus the drops, each with its
     * sign in the order it was registered, plus the cash payments. Negative after a drop above it (decision C7). Once closed, the amount frozen at the
     * closing, whatever is passed in.
     *
     * @param cashPayments the confirmed cash payments linked to this session
     */
    public Money expectedAmount(Money cashPayments) {
        Objects.requireNonNull(cashPayments, "cashPayments");
        if (!isOpen()) {
            return expectedAmount;
        }
        return openingFloat.plus(totalMovements()).plus(cashPayments);
    }

    /** The expected amount frozen at the closing; empty while open. */
    public Optional<Money> frozenExpectedAmount() {
        return Optional.ofNullable(expectedAmount);
    }

    /** The cash payments counted at the closing, derived from the frozen expected amount; empty while open. */
    public Optional<Money> frozenCashPayments() {
        return frozenExpectedAmount().map(frozen -> frozen.minus(openingFloat).minus(totalMovements()));
    }

    /** The count minus the frozen expected amount: positive is a surplus, negative a shortfall. Empty while open. */
    public Optional<Money> difference() {
        return countedAmount().flatMap(counted -> frozenExpectedAmount().map(counted::minus));
    }

    /**
     * Whether the expected amount may be shown (decision C4 of task 2.4): the closing is blind, so an
     * open session hides it from whoever is not an {@code ADMIN}. A closed one shows it to everyone.
     */
    public boolean revealsExpectedAmountTo(boolean viewerIsAdmin) {
        return viewerIsAdmin || !isOpen();
    }

    public Money totalDrops() {
        return totalOf(CashMovementType.CASH_DROP);
    }

    public Money totalSupplies() {
        return totalOf(CashMovementType.CASH_SUPPLY);
    }

    /**
     * Every movement with its sign, in the order it was registered: a movement and its opposite cancel
     * out without the sum passing through the sum of the supplies alone, which may be out of range.
     */
    private Money totalMovements() {
        return movements.stream().map(CashMovement::signedAmount).reduce(Money.ZERO, Money::plus);
    }

    private Money totalOf(CashMovementType type) {
        return movements.stream()
                .filter(movement -> movement.is(type))
                .map(CashMovement::amount)
                .reduce(Money.ZERO, Money::plus);
    }

    private void requireOpen() {
        if (!status.acceptsMovements()) {
            throw new CashDrawerSessionClosedException("Cash drawer session " + id + " is closed");
        }
    }

    // ------------------------------------------------------------------ reading

    public CashDrawerSessionId id() {
        return new CashDrawerSessionId(id);
    }

    public UUID propertyId() {
        return propertyId;
    }

    public CashDrawerSessionStatus status() {
        return status;
    }

    public boolean isOpen() {
        return status.acceptsMovements();
    }

    public Money openingFloat() {
        return openingFloat;
    }

    public UUID openedBy() {
        return openedBy;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public Optional<UUID> closedBy() {
        return Optional.ofNullable(closedBy);
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Optional<Money> countedAmount() {
        return Optional.ofNullable(countedAmount);
    }

    public Optional<String> closingNote() {
        return Optional.ofNullable(closingNote);
    }

    /** Every drop and supply, in the order it was registered. */
    public List<CashMovement> movements() {
        return Collections.unmodifiableList(movements);
    }
}
