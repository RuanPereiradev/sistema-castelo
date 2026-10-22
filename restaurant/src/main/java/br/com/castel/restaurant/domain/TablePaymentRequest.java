package br.com.castel.restaurant.domain;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.billing.api.PaymentId;
import br.com.castel.sharedkernel.AuditedEntity;
import br.com.castel.sharedkernel.Money;
import br.com.castel.sharedkernel.Percentage;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A payment the customer sends from the phone of the table, by Pix, for the items they chose,
 * waiting for the waiter to see the receipt (decisions #2, #4, #7 and #8 of task qr-table-payment).
 *
 * <p>Created {@code PENDING}, it reserves its items on the {@link Tab} and freezes the amount — the
 * items and the service charge over the ones that count for it (decisions #10 and #20) — and the
 * static Pix code of that amount (decision #22). Nobody else at the table pays the same items while
 * it waits (decision #11). The waiter {@linkplain #confirm confirms} it, and the part goes on the
 * folio of the tab; or {@linkplain #reject rejects} it, and the items are free again (decision #13).
 * Nobody answering in {@link #PENDING_WINDOW} makes it {@linkplain #expire expire}. A confirmed
 * request is {@linkplain #refund refunded} by the waiter, which frees its items (decision #21).
 *
 * <p>Past its window a request is still stored as {@code PENDING} until the expiry job writes it, so
 * every reading goes through {@link #statusAt} and every answer of the waiter refuses it.
 */
@Entity
@Table(name = "table_payment_request")
public class TablePaymentRequest extends AuditedEntity {

    /** How long a request waits for the waiter (decision #8). */
    public static final Duration PENDING_WINDOW = Duration.ofMinutes(30);

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private TablePaymentRequestId id;

    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @AttributeOverride(name = "value", column = @Column(name = "dining_table_id", nullable = false, updatable = false))
    private DiningTableId diningTableId;

    @AttributeOverride(name = "value", column = @Column(name = "tab_id", nullable = false, updatable = false))
    private TabId tabId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TablePaymentRequestStatus status;

    @Column(name = "subtotal", nullable = false, updatable = false)
    private Money subtotal;

    @Column(name = "service_charge", nullable = false, updatable = false)
    private Money serviceCharge;

    /** The rate the charge was calculated with, as the fraction {@link Percentage} keeps. */
    @Column(name = "service_charge_rate", nullable = false, updatable = false, precision = 5, scale = 4)
    private BigDecimal serviceChargeRateFraction;

    /** Whether the customer chose to pay the service charge on these units (decision #38). */
    @Column(name = "include_service_charge", nullable = false, updatable = false)
    private boolean includeServiceCharge;

    @Column(name = "customer_name", nullable = false, updatable = false)
    private String customerName;

    @Column(name = "customer_device_id", nullable = false, updatable = false)
    private String customerDeviceId;

    @Column(name = "pix_payload", nullable = false, updatable = false)
    private String pixPayload;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "part_charge_id")
    private UUID partChargeId;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refunded_by")
    private UUID refundedBy;

    @Column(name = "refund_reason")
    private String refundReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "table_payment_request_item",
            joinColumns = @JoinColumn(name = "table_payment_request_id"))
    private List<TablePaymentRequestItem> items = new ArrayList<>();

    protected TablePaymentRequest() {
        // for JPA
    }

    /**
     * A payment sent from the phone of the table, born {@code PENDING}, reserving its units.
     *
     * <p>In this order: the customer (decision #46); a Pix receiver configured (decision #15); then
     * the tab, which checks its status and every choice ({@link Tab#reserveForPayment}).
     *
     * @param tab the active tab of the table, locked for the reservation
     * @param includeServiceCharge whether the customer pays the service charge on these units (decision #38)
     * @param currentRate the rate of the service charge in the setting now; the tab uses its frozen
     *     one while {@code CLOSING}
     * @throws InvalidCustomerNameException if the name is missing, blank or past 40 characters
     * @throws InvalidCustomerDeviceIdException if the device code is missing or malformed
     * @throws PixReceiverNotConfiguredException if no Pix receiver is configured
     * @see Tab#reserveForPayment for the refusals of the items
     */
    public static TablePaymentRequest request(UUID propertyId, Tab tab, String customerName, String customerDeviceId,
            List<Tab.PartChoice> choices, boolean includeServiceCharge, Percentage currentRate,
            Optional<PixReceiver> receiver, Instant requestedAt) {
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(tab, "tab");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(requestedAt, "requestedAt");
        String validCustomerName = TableOrderRequest.requireCustomerName(customerName);
        String validCustomerDeviceId = TableOrderRequest.requireCustomerDeviceId(customerDeviceId);
        PixReceiver pixReceiver = receiver.orElseThrow(
                () -> new PixReceiverNotConfiguredException("No Pix receiver is configured"));
        DiningTableId diningTableId = tab.diningTableId().orElseThrow(
                () -> new InvalidTablePaymentRequestException("Only the tab of a dining table is paid from its phone"));
        TablePaymentRequest request = new TablePaymentRequest();
        request.id = TablePaymentRequestId.newId();
        Tab.PaymentPart part = tab.reserveForPayment(choices, includeServiceCharge, currentRate);
        request.propertyId = propertyId;
        request.customerName = validCustomerName;
        request.customerDeviceId = validCustomerDeviceId;
        request.includeServiceCharge = includeServiceCharge;
        request.diningTableId = diningTableId;
        request.tabId = tab.id();
        request.status = TablePaymentRequestStatus.PENDING;
        request.items = new ArrayList<>(part.items());
        request.subtotal = part.items().stream().map(TablePaymentRequestItem::lineTotal).reduce(Money.ZERO, Money::plus);
        request.serviceCharge = part.items().stream()
                .map(TablePaymentRequestItem::serviceCharge)
                .reduce(Money.ZERO, Money::plus);
        request.serviceChargeRateFraction = part.serviceChargeRate().fraction();
        request.requestedAt = requestedAt;
        request.expiresAt = requestedAt.plus(PENDING_WINDOW);
        request.pixPayload = pixReceiver.pixPayloadFor(request.amount(), request.id.value().toString());
        return request;
    }

    // ------------------------------------------------------------------ the waiter

    /**
     * Refuses a request the waiter can no longer answer: past its window, then already answered.
     * Changes nothing.
     *
     * @throws TablePaymentRequestExpiredException if it is stored {@code EXPIRED}, or {@code PENDING}
     *     past its window
     * @throws TablePaymentRequestNotPendingException if it was confirmed or rejected
     */
    public void requireAnswerableAt(Instant at) {
        Objects.requireNonNull(at, "at");
        if (status.isExpired() || isExpiredAt(at)) {
            throw new TablePaymentRequestExpiredException("Payment request " + id.value() + " expired at " + expiresAt);
        }
        if (status.isResolved()) {
            throw new TablePaymentRequestNotPendingException("Payment request " + id.value() + " is already " + status);
        }
    }

    /**
     * The waiter saw the receipt (decision #8): the part goes on the folio of the tab and its items
     * become paid ({@link Tab#payPart}).
     *
     * @param tab the tab of the request, locked
     * @throws TablePaymentRequestExpiredException if its window is over
     * @throws TablePaymentRequestNotPendingException if it was already answered
     */
    public void confirm(Tab tab, TabBilling billing, UUID confirmedBy, Instant confirmedAt) {
        Objects.requireNonNull(confirmedBy, "confirmedBy");
        requireAnswerableAt(confirmedAt);
        Tab.PaidPart paid = requireOwnTab(tab).payPart(this, billing);
        this.status = TablePaymentRequestStatus.CONFIRMED;
        this.resolvedBy = confirmedBy;
        this.resolvedAt = confirmedAt;
        this.paymentId = paid.paymentId().value();
        this.partChargeId = paid.partChargeId().value();
    }

    /**
     * The receipt does not match (decision #13): the items are free again.
     *
     * @throws TablePaymentRequestExpiredException if its window is over
     * @throws TablePaymentRequestNotPendingException if it was already answered
     */
    public void reject(Tab tab, UUID rejectedBy, Instant rejectedAt) {
        Objects.requireNonNull(rejectedBy, "rejectedBy");
        requireAnswerableAt(rejectedAt);
        requireOwnTab(tab).releasePaymentOf(this);
        this.status = TablePaymentRequestStatus.REJECTED;
        this.resolvedBy = rejectedBy;
        this.resolvedAt = rejectedAt;
    }

    /**
     * Nobody answered in time (decision #8): the items are free again. No author is recorded.
     *
     * @throws TablePaymentRequestNotPendingException if it was already answered or expired
     * @throws IllegalStateException if its window is not over yet: only the expiry job calls this,
     *     and it reads the overdue requests only
     */
    public void expire(Tab tab, Instant at) {
        Objects.requireNonNull(at, "at");
        if (status.isResolved()) {
            throw new TablePaymentRequestNotPendingException("Payment request " + id.value() + " is already " + status);
        }
        if (!isExpiredAt(at)) {
            throw new IllegalStateException("Payment request " + id.value() + " is open until " + expiresAt);
        }
        requireOwnTab(tab).releasePaymentOf(this);
        this.status = TablePaymentRequestStatus.EXPIRED;
        this.resolvedAt = at;
    }

    /**
     * Undoes a confirmed payment (decision #21): the Pix refunded, the charge of the part reversed,
     * the items free again ({@link Tab#refundPart}). The request stays {@code CONFIRMED}, with the
     * refund recorded (decision #30).
     *
     * @param reason why, which billing requires on the refund and on the reversal
     * @throws TablePaymentRequestNotRefundableException if it is not confirmed, or already refunded
     */
    public void refund(Tab tab, String reason, TabBilling billing, UUID refundedBy, Instant refundedAt) {
        Objects.requireNonNull(refundedBy, "refundedBy");
        Objects.requireNonNull(refundedAt, "refundedAt");
        if (!status.isConfirmed() || isRefunded()) {
            throw new TablePaymentRequestNotRefundableException(
                    "Payment request " + id.value() + " is " + status + (isRefunded() ? ", refunded" : ""));
        }
        requireOwnTab(tab).refundPart(this, reason, billing);
        this.refundReason = reason.trim();
        this.refundedBy = refundedBy;
        this.refundedAt = refundedAt;
    }

    private Tab requireOwnTab(Tab tab) {
        Objects.requireNonNull(tab, "tab");
        if (!tab.id().equals(tabId)) {
            throw new IllegalStateException("Payment request " + id.value() + " belongs to tab " + tabId.value());
        }
        return tab;
    }

    // ------------------------------------------------------------------ reading

    /** Still stored as {@code PENDING}, with its window over: from {@link #expiresAt()} on. */
    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return status.isPending() && !now.isBefore(expiresAt);
    }

    /** The status as the phone and the floor read it: {@code EXPIRED} once the window is over. */
    public TablePaymentRequestStatus statusAt(Instant now) {
        return isExpiredAt(now) ? TablePaymentRequestStatus.EXPIRED : status;
    }

    /** What the Pix charges: the items and their service charge, frozen at creation (decision #20). */
    public Money amount() {
        return subtotal.plus(serviceCharge);
    }

    public boolean isRefunded() {
        return refundedAt != null;
    }

    public TablePaymentRequestId id() {
        return id;
    }

    public UUID propertyId() {
        return propertyId;
    }

    public DiningTableId diningTableId() {
        return diningTableId;
    }

    public TabId tabId() {
        return tabId;
    }

    /** The status as stored; read {@link #statusAt} to see a request past its window as expired. */
    public TablePaymentRequestStatus status() {
        return status;
    }

    public Money subtotal() {
        return subtotal;
    }

    public Money serviceCharge() {
        return serviceCharge;
    }

    public Percentage serviceChargeRate() {
        return Percentage.ofFraction(serviceChargeRateFraction);
    }

    public boolean includeServiceCharge() {
        return includeServiceCharge;
    }

    public String customerName() {
        return customerName;
    }

    /** The device that sent it; never shown to another device. */
    public String customerDeviceId() {
        return customerDeviceId;
    }

    /** The static Pix code ("copia e cola") of the amount, frozen at creation (decision #22). */
    public String pixPayload() {
        return pixPayload;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Optional<Instant> resolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }

    public Optional<UUID> resolvedBy() {
        return Optional.ofNullable(resolvedBy);
    }

    /** The Pix registered on the folio; set only on {@code CONFIRMED}. */
    public Optional<PaymentId> paymentId() {
        return Optional.ofNullable(paymentId).map(PaymentId::new);
    }

    /** The charge of the part on the folio; set only on {@code CONFIRMED}. */
    public Optional<ChargeId> partChargeId() {
        return Optional.ofNullable(partChargeId).map(ChargeId::new);
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

    /** The units the request pays, item by item, each with its frozen value and share of the charge. */
    public List<TablePaymentRequestItem> items() {
        return Collections.unmodifiableList(items);
    }
}
