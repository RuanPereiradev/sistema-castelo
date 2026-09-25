package br.com.castel.billing.domain;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.billing.api.ChargeRequest;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.FolioOwner;
import br.com.castel.billing.api.FolioReference;
import br.com.castel.billing.api.FolioStatus;
import br.com.castel.billing.api.FolioType;
import br.com.castel.billing.api.OwnerType;
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
 * The account of a stay or of a tab: what was charged, what was paid, and the difference.
 *
 * <p>A {@link FolioType#STAY} folio belongs to a reservation and is found by its
 * {@link FolioReference}, whose code is the room number (decision #2 of task 0.6). A
 * {@link FolioType#TAB} folio belongs to a tab of someone who is not staying, and has no reference.
 *
 * <p>The {@linkplain #balance() balance} is calculated on every read, never stored: the charges, with
 * their sign, minus the confirmed payments. Charges are append-only; a mistake is reversed by an
 * opposing charge (decision #7 of task 1.3), and a payment is refunded, never removed (decision #2).
 *
 * <p>Closing is explicit (decision #8) and only with the balance at exactly zero (decision #4). A
 * closed folio refuses every write. Rules about the set of folios — one folio per owner, one open
 * stay per reference code, an idempotency key used once across folios — are checked by the use case
 * and held by the database.
 */
@Entity
@Table(name = "folio")
public class Folio extends AuditedEntity {

    /** Maximum length of a reference code after trimming, matching the column. */
    public static final int MAXIMUM_REFERENCE_CODE_LENGTH = 20;

    /** Maximum length of a reference label after trimming, matching the column. */
    public static final int MAXIMUM_REFERENCE_LABEL_LENGTH = 150;

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "folio_type", nullable = false, length = 10)
    private FolioType type;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private FolioStatus status;

    @Column(name = "reference_code", length = MAXIMUM_REFERENCE_CODE_LENGTH)
    private String referenceCode;

    @Column(name = "reference_label", length = MAXIMUM_REFERENCE_LABEL_LENGTH)
    private String referenceLabel;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    /*
     * Both lists are fetched by subselect: Hibernate refuses to join-fetch two lists in one query, and
     * a join would also put the charges inside the SELECT ... FOR UPDATE that locks the folio.
     * Ordered as they were written: by creation time, to the microsecond, then by id, a version 7
     * UUID, which alone orders only across milliseconds.
     */
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "folio_id", nullable = false, updatable = false)
    @OrderBy("createdAt, id")
    @Fetch(FetchMode.SUBSELECT)
    private List<Charge> charges = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "folio_id", nullable = false, updatable = false)
    @OrderBy("createdAt, id")
    @Fetch(FetchMode.SUBSELECT)
    private List<Payment> payments = new ArrayList<>();

    protected Folio() {
        // for JPA
    }

    private Folio(UUID propertyId, FolioType type, UUID ownerId, FolioReference reference, Instant openedAt) {
        this.id = FolioId.newId().value();
        this.propertyId = propertyId;
        this.type = type;
        this.ownerId = ownerId;
        this.status = FolioStatus.OPEN;
        this.openedAt = openedAt;
        applyReference(reference);
    }

    /**
     * The account of a stay, born open.
     *
     * @param reservation must be a {@link OwnerType#RESERVATION}; anything else is a programming
     *     error between modules, not a business rule
     * @throws InvalidFolioReferenceException if the code is outside 1 to 20 characters or the label
     *     outside 1 to 150, once trimmed
     */
    public static Folio openForStay(
            UUID propertyId, FolioOwner reservation, FolioReference reference, Instant openedAt) {
        requireOpeningFields(propertyId, reservation, openedAt);
        requireOwnerType(reservation, OwnerType.RESERVATION);
        Objects.requireNonNull(reference, "reference");
        return new Folio(propertyId, FolioType.STAY, reservation.value(), validReference(reference), openedAt);
    }

    /**
     * The account of a tab of someone who is not staying, born open and with no reference.
     *
     * @param tab must be a {@link OwnerType#TAB}; anything else is a programming error between modules
     */
    public static Folio openForTab(UUID propertyId, FolioOwner tab, Instant openedAt) {
        requireOpeningFields(propertyId, tab, openedAt);
        requireOwnerType(tab, OwnerType.TAB);
        return new Folio(propertyId, FolioType.TAB, tab.value(), null, openedAt);
    }

    // ------------------------------------------------------------------ charges

    /**
     * Writes a room night or the total of a tab.
     *
     * @throws FolioClosedException if the folio is closed
     * @throws ChargeTypeNotAcceptedException if a room night is posted on the folio of a tab
     * @throws InvalidChargeAmountException if the amount is not greater than zero
     * @throws InvalidChargeDescriptionException if the description is blank or longer than 255
     */
    public Charge post(ChargeRequest request) {
        Objects.requireNonNull(request, "request");
        requireOpen();
        ChargeType chargeType = ChargeType.of(request.source().type());
        if (!chargeType.isAcceptedOn(type)) {
            throw new ChargeTypeNotAcceptedException("A " + type + " folio does not accept a " + chargeType + " charge");
        }
        if (!request.amount().isPositive()) {
            throw new InvalidChargeAmountException("A posted charge must be greater than zero");
        }
        String description = validDescription(request.description());
        Charge charge = switch (chargeType) {
            case ROOM_NIGHT -> RoomNightCharge.of(request.amount(), description, request.source().id());
            case TAB -> TabCharge.of(request.amount(), description, request.source().id());
            case ADJUSTMENT -> throw new IllegalStateException("No source posts an adjustment");
        };
        charges.add(charge);
        return charge;
    }

    /**
     * Writes a correction, up or down, authorized by an {@code ADMIN}.
     *
     * @throws FolioClosedException if the folio is closed
     * @throws InvalidChargeAmountException if the amount is missing or zero
     * @throws InvalidChargeDescriptionException if the description is blank or longer than 255
     * @throws InvalidChargeReasonException if the reason is blank or longer than 500
     */
    public AdjustmentCharge postAdjustment(Money amount, String description, String reason, UUID authorizedBy) {
        Objects.requireNonNull(authorizedBy, "authorizedBy");
        requireOpen();
        if (amount == null || amount.isZero()) {
            throw new InvalidChargeAmountException("An adjustment must be different from zero");
        }
        AdjustmentCharge adjustment =
                AdjustmentCharge.of(amount, validDescription(description), validReason(reason), authorizedBy);
        charges.add(adjustment);
        return adjustment;
    }

    /**
     * Undoes a room night or a tab charge with the opposing charge, whole (decision #7 of task 1.3).
     * The original stays on the folio, and the balance falls by exactly its amount.
     *
     * @return the opposing charge
     * @throws FolioClosedException if the folio is closed
     * @throws ChargeNotFoundException if the folio has no such charge
     * @throws ChargeNotReversibleException if the charge is an adjustment or itself a reversal
     * @throws ChargeAlreadyReversedException if the charge was already reversed
     * @throws InvalidChargeReasonException if the reason is blank or longer than 500
     */
    public Charge reverse(ChargeId chargeId, String reason) {
        Objects.requireNonNull(chargeId, "chargeId");
        requireOpen();
        Charge original = charge(chargeId);
        if (original.isReversed()) {
            throw new ChargeAlreadyReversedException("Charge " + chargeId.value() + " was already reversed");
        }
        if (!original.isReversible()) {
            throw new ChargeNotReversibleException(
                    "Charge " + chargeId.value() + " is an adjustment or a reversal, and cannot be reversed");
        }
        Charge reversal = original.reversal(validReason(reason));
        charges.add(reversal);
        original.markReversedBy(reversal.id());
        return reversal;
    }

    // ------------------------------------------------------------------ payments

    /**
     * Registers a payment received at the counter.
     *
     * <p>A retry is recognised before any other rule, even on a closed folio (decision #5 of task
     * 1.3): a key this folio already holds with the same method and amount answers the payment
     * already registered, refunded or not, and writes nothing.
     *
     * @return the new payment, or the one already registered under this key
     * @throws InvalidIdempotencyKeyException if the key is blank or longer than 100 characters
     * @throws IdempotencyKeyReusedException if this folio holds the key with another method or amount
     * @throws FolioClosedException if the folio is closed
     * @throws PaymentMethodNotAcceptedException if the method is not registered by hand
     * @throws InvalidPaymentAmountException if the amount is missing or not greater than zero
     * @throws PaymentExceedsBalanceException if a tab folio would be paid beyond what it owes
     */
    public Payment receive(
            PaymentMethod method, Money amount, String idempotencyKey, UUID receivedBy, Instant paidAt) {
        String key = Payment.requireValidIdempotencyKey(idempotencyKey);
        Optional<Payment> earlier = paymentWithKey(key);
        if (earlier.isPresent()) {
            return replay(earlier.get(), method, amount);
        }
        Objects.requireNonNull(method, "method");
        requireOpen();
        if (!method.acceptsManualEntry()) {
            throw new PaymentMethodNotAcceptedException(method + " is not registered by hand");
        }
        if (amount == null || !amount.isPositive()) {
            throw new InvalidPaymentAmountException("A payment must be greater than zero");
        }
        if (!acceptsPaymentBeyondBalance() && amount.isGreaterThan(balance())) {
            throw new PaymentExceedsBalanceException("A tab folio takes no payment above its balance");
        }
        Payment payment = Payment.receivedAtCounter(method, amount, key, receivedBy, paidAt);
        payments.add(payment);
        return payment;
    }

    /**
     * Refunds a confirmed payment (decision #2 of task 1.3). The balance goes up by exactly its amount.
     *
     * @throws FolioClosedException if the folio is closed
     * @throws PaymentNotFoundException if the folio has no such payment
     * @throws PaymentAlreadyRefundedException if the payment is not confirmed
     * @throws InvalidPaymentRefundReasonException if the reason is blank or longer than 500
     */
    public void refund(PaymentId paymentId, String reason, UUID refundedBy, Instant refundedAt) {
        Objects.requireNonNull(paymentId, "paymentId");
        requireOpen();
        payment(paymentId).refund(reason, refundedBy, refundedAt);
    }

    /** The payment this folio registered under the key, trimmed; how a retry is recognised. */
    public Optional<Payment> paymentWithKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        String key = idempotencyKey.trim();
        return payments.stream().filter(payment -> payment.hasIdempotencyKey(key)).findFirst();
    }

    // ------------------------------------------------------------------ reference and closing

    /**
     * Points the folio of a stay at another code, when the guest changes room (decision #8 of task
     * 0.6). Whether another open stay already uses the code is a rule about the set of folios, checked
     * by the use case.
     *
     * @throws FolioClosedException if the folio is closed
     * @throws InvalidFolioReferenceException if the code or the label is out of bounds
     */
    public void changeReference(FolioReference newReference) {
        if (type != FolioType.STAY) {
            throw new IllegalStateException("Only the folio of a stay has a reference; folio " + id + " is a tab");
        }
        Objects.requireNonNull(newReference, "newReference");
        requireOpen();
        applyReference(validReference(newReference));
    }

    /**
     * Closes the folio. Only with a balance of exactly zero (decision #4 of task 1.3): a difference is
     * settled first with an adjustment, by an {@code ADMIN}. A folio with no charge closes.
     *
     * @throws FolioClosedException if the folio is already closed
     * @throws FolioBalanceNotZeroException if the balance is not zero
     */
    public void close(UUID closedBy, Instant closedAt) {
        Objects.requireNonNull(closedBy, "closedBy");
        Objects.requireNonNull(closedAt, "closedAt");
        requireOpen();
        if (!balance().isZero()) {
            throw new FolioBalanceNotZeroException("Folio " + id + " has a balance of " + balance());
        }
        this.status = FolioStatus.CLOSED;
        this.closedBy = closedBy;
        this.closedAt = closedAt;
    }

    // ------------------------------------------------------------------ totals

    /** What the folio still owes: every charge, with its sign, minus the confirmed payments. */
    public Money balance() {
        return totalCharges().minus(totalPayments());
    }

    /** The sum of every charge, reversals and adjustments included, each with its sign. */
    public Money totalCharges() {
        return charges.stream().map(Charge::amount).reduce(Money.ZERO, Money::plus);
    }

    /** The sum of the payments that count: confirmed ones. A refunded payment does not. */
    public Money totalPayments() {
        return payments.stream()
                .filter(Payment::countsTowardsBalance)
                .map(Payment::amount)
                .reduce(Money.ZERO, Money::plus);
    }

    // ------------------------------------------------------------------ guards

    private static void requireOpeningFields(UUID propertyId, FolioOwner owner, Instant openedAt) {
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(openedAt, "openedAt");
    }

    /**
     * A folio of the wrong kind of owner is a programming error between modules, not a business rule
     * (decision #18 of task 1.3). {@code IllegalStateException} and not {@code IllegalArgumentException},
     * which rule C5 of the architecture tests forbids in domain code.
     */
    private static void requireOwnerType(FolioOwner owner, OwnerType expected) {
        if (owner.type() != expected) {
            throw new IllegalStateException("Expected an owner of type " + expected + ", got " + owner.type());
        }
    }

    private void requireOpen() {
        if (!status.acceptsPostings()) {
            throw new FolioClosedException("Folio " + id + " is closed");
        }
    }

    /** A tab folio is paid only up to what it owes; a stay may hold credit (decision #1 of task 1.3). */
    private boolean acceptsPaymentBeyondBalance() {
        return type == FolioType.STAY;
    }

    private Payment replay(Payment earlier, PaymentMethod method, Money amount) {
        if (!earlier.matches(method, amount)) {
            throw new IdempotencyKeyReusedException(
                    "The idempotency key was used by a payment of another method or amount");
        }
        return earlier;
    }

    private static FolioReference validReference(FolioReference reference) {
        return new FolioReference(
                BoundedText.require(reference.code(), MAXIMUM_REFERENCE_CODE_LENGTH, "reference code",
                        InvalidFolioReferenceException::new),
                BoundedText.require(reference.label(), MAXIMUM_REFERENCE_LABEL_LENGTH, "reference label",
                        InvalidFolioReferenceException::new));
    }

    private void applyReference(FolioReference reference) {
        this.referenceCode = reference == null ? null : reference.code();
        this.referenceLabel = reference == null ? null : reference.label();
    }

    private static String validDescription(String description) {
        return BoundedText.require(description, Charge.MAXIMUM_DESCRIPTION_LENGTH, "charge description",
                InvalidChargeDescriptionException::new);
    }

    private static String validReason(String reason) {
        return BoundedText.require(reason, Charge.MAXIMUM_REASON_LENGTH, "charge reason",
                InvalidChargeReasonException::new);
    }

    /**
     * Every charge, after telling each one which charge reverses it. The link is stored on the
     * reversal only, so it is filled in here, before any charge is handed out.
     */
    private List<Charge> linkedCharges() {
        charges.forEach(charge -> charge.reversalOf().ifPresent(original -> charges.stream()
                .filter(candidate -> candidate.id().equals(original))
                .forEach(candidate -> candidate.markReversedBy(charge.id()))));
        return charges;
    }

    /**
     * @throws ChargeNotFoundException if the folio has no such charge
     */
    public Charge charge(ChargeId chargeId) {
        Objects.requireNonNull(chargeId, "chargeId");
        return linkedCharges().stream()
                .filter(charge -> charge.id().equals(chargeId))
                .findFirst()
                .orElseThrow(() -> new ChargeNotFoundException(
                        "Folio " + id + " has no charge " + chargeId.value()));
    }

    /**
     * @throws PaymentNotFoundException if the folio has no such payment
     */
    public Payment payment(PaymentId paymentId) {
        Objects.requireNonNull(paymentId, "paymentId");
        return payments.stream()
                .filter(payment -> payment.id().equals(paymentId))
                .findFirst()
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Folio " + id + " has no payment " + paymentId.value()));
    }

    // ------------------------------------------------------------------ reading

    public FolioId id() {
        return new FolioId(id);
    }

    public UUID propertyId() {
        return propertyId;
    }

    public FolioType type() {
        return type;
    }

    public FolioStatus status() {
        return status;
    }

    public boolean isOpen() {
        return status.acceptsPostings();
    }

    /** What the folio was opened for: a reservation for a stay, a tab for a tab. */
    public FolioOwner owner() {
        return new FolioOwner(type == FolioType.STAY ? OwnerType.RESERVATION : OwnerType.TAB, ownerId);
    }

    /** The code and label of a stay; empty for a tab. */
    public Optional<FolioReference> reference() {
        return referenceCode == null
                ? Optional.empty()
                : Optional.of(new FolioReference(referenceCode, referenceLabel));
    }

    public Instant openedAt() {
        return openedAt;
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Optional<UUID> closedBy() {
        return Optional.ofNullable(closedBy);
    }

    /** Every charge, in the order it was written. */
    public List<Charge> charges() {
        return Collections.unmodifiableList(linkedCharges());
    }

    /** Every payment, refunded ones included, in the order it was registered. */
    public List<Payment> payments() {
        return Collections.unmodifiableList(payments);
    }
}
