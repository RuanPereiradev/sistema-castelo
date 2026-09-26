package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.AuditedEntity;
import br.com.castel.sharedkernel.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Money received against a {@link Folio}.
 *
 * <p>A payment registered at the counter is born {@link PaymentStatus#CONFIRMED}, with the operator
 * who received it. It is never removed: a payment made by mistake is refunded by an {@code ADMIN}
 * (decision #2 of task 1.3), goes to {@link PaymentStatus#REFUNDED} and stops counting towards the
 * balance, keeping who refunded it, when and why.
 *
 * <p>The idempotency key is what makes a double click, or a retry after a lost answer, register the
 * payment once (decision #5 of task 1.3). It is unique across every folio ({@code uk_payment_idempotency}).
 *
 * <p>Part of the {@link Folio} aggregate and changed only through it. Neither the cash drawer session
 * (task 2.4) nor the payment intent (version 1.1) is mapped yet.
 */
@Entity
@Table(name = "payment")
public class Payment extends AuditedEntity {

    /** Maximum length of an idempotency key after trimming, matching the column. */
    public static final int MAXIMUM_IDEMPOTENCY_KEY_LENGTH = 100;

    /** Maximum length of the reason of a refund. */
    public static final int MAXIMUM_REFUND_REASON_LENGTH = 500;

    @Id
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "amount", nullable = false)
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "idempotency_key", nullable = false, length = MAXIMUM_IDEMPOTENCY_KEY_LENGTH)
    private String idempotencyKey;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refunded_by")
    private UUID refundedBy;

    @Column(name = "refund_reason")
    private String refundReason;

    protected Payment() {
        // for JPA
    }

    private Payment(PaymentMethod method, Money amount, String idempotencyKey, UUID receivedBy, Instant paidAt) {
        this.id = PaymentId.newId().value();
        this.method = method;
        this.amount = amount;
        this.status = PaymentStatus.CONFIRMED;
        this.idempotencyKey = idempotencyKey;
        this.receivedBy = receivedBy;
        this.paidAt = paidAt;
    }

    /** A payment registered by an operator. Receives values the aggregate already validated. */
    static Payment receivedAtCounter(
            PaymentMethod method, Money amount, String idempotencyKey, UUID receivedBy, Instant paidAt) {
        return new Payment(
                method,
                amount,
                idempotencyKey,
                Objects.requireNonNull(receivedBy, "receivedBy"),
                Objects.requireNonNull(paidAt, "paidAt"));
    }

    /**
     * The key trimmed, as it is compared and stored.
     *
     * @throws InvalidIdempotencyKeyException if the key is missing, blank or longer than 100
     *     characters once trimmed
     */
    public static String requireValidIdempotencyKey(String idempotencyKey) {
        return BoundedText.require(
                idempotencyKey, MAXIMUM_IDEMPOTENCY_KEY_LENGTH, "idempotency key", InvalidIdempotencyKeyException::new);
    }

    /**
     * @throws PaymentAlreadyRefundedException if the payment is not confirmed
     * @throws InvalidPaymentRefundReasonException if the reason is blank or longer than 500 characters
     */
    void refund(String reason, UUID refundedBy, Instant refundedAt) {
        if (!status.acceptsRefund()) {
            throw new PaymentAlreadyRefundedException("Payment " + id + " is " + status + ", not confirmed");
        }
        this.refundReason = BoundedText.require(
                reason, MAXIMUM_REFUND_REASON_LENGTH, "refund reason", InvalidPaymentRefundReasonException::new);
        this.refundedBy = Objects.requireNonNull(refundedBy, "refundedBy");
        this.refundedAt = Objects.requireNonNull(refundedAt, "refundedAt");
        this.status = PaymentStatus.REFUNDED;
    }

    /** Whether a retry with this method and amount is the same payment as this one. */
    public boolean matches(PaymentMethod method, Money amount) {
        return this.method == method && this.amount.equals(amount);
    }

    boolean hasIdempotencyKey(String trimmedKey) {
        return idempotencyKey.equals(trimmedKey);
    }

    public boolean isConfirmed() {
        return status == PaymentStatus.CONFIRMED;
    }

    /** Whether this payment takes from the balance of the folio. */
    public boolean countsTowardsBalance() {
        return status.countsTowardsBalance();
    }

    public PaymentId id() {
        return new PaymentId(id);
    }

    public PaymentMethod method() {
        return method;
    }

    public Money amount() {
        return amount;
    }

    public PaymentStatus status() {
        return status;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Instant paidAt() {
        return paidAt;
    }

    /** The operator who registered the payment; empty only for an online payment (version 1.1). */
    public Optional<UUID> receivedBy() {
        return Optional.ofNullable(receivedBy);
    }

    public Optional<Instant> refundedAt() {
        return Optional.ofNullable(refundedAt);
    }

    public Optional<UUID> refundedBy() {
        return Optional.ofNullable(refundedBy);
    }

    public Optional<String> refundReason() {
        return Optional.ofNullable(refundReason);
    }
}
