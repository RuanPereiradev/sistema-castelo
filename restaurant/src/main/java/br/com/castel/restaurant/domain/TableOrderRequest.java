package br.com.castel.restaurant.domain;

import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.sharedkernel.AuditedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * An order the customer sent from the QR code of a dining table, waiting for a waiter (decisions #6
 * and #8).
 *
 * <p>Nothing of it reaches the tab, the kitchen or the folio while it is {@code PENDING}. A waiter
 * {@linkplain #confirm confirms} it, and each line becomes one {@link Tab#addItem} on the table's
 * tab, as that waiter (decision #7); or {@linkplain #reject rejects} it as a whole (decision #11).
 * Nobody answering in {@link #PENDING_WINDOW} makes it {@linkplain #expire expire} (decision #14).
 *
 * <p>At most one {@code PENDING} request per table (decision #15) is a rule about the set of
 * requests, so it is held by {@code idx_table_order_request_pending_by_table} and not here.
 *
 * <p>Past its window a request is still stored as {@code PENDING} until the expiry job writes it
 * (decision #29), so every reading goes through {@link #statusAt} and every resolution refuses it.
 */
@Entity
@Table(name = "table_order_request")
public class TableOrderRequest extends AuditedEntity {

    /** How long a request waits for a waiter (decision #14). */
    public static final Duration PENDING_WINDOW = Duration.ofMinutes(30);

    /** How many lines one request takes (decision #20). */
    public static final int MAXIMUM_LINES = 20;

    /** Longest name a customer types on the phone, once trimmed (decision #46 of task qr-table-payment). */
    public static final int MAXIMUM_CUSTOMER_NAME_LENGTH = 40;

    /** What a device code is made of: 8 to 64 letters, digits, hyphens or underscores (decision #46). */
    private static final Pattern CUSTOMER_DEVICE_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private TableOrderRequestId id;

    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @AttributeOverride(name = "value", column = @Column(name = "dining_table_id", nullable = false, updatable = false))
    private DiningTableId diningTableId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TableOrderRequestStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @AttributeOverride(name = "value", column = @Column(name = "tab_id"))
    private TabId tabId;

    /* Who sent it from the phone (decision #40 of task qr-table-payment); null only on rows from before V14. */
    @Column(name = "customer_name", updatable = false)
    private String customerName;

    @Column(name = "customer_device_id", updatable = false)
    private String customerDeviceId;

    /*
     * The line owns table_order_request_id, like TabItem owns tab_id, so inserting a request writes
     * no UPDATE of the collection.
     */
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "table_order_request_id", nullable = false, insertable = false, updatable = false)
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("position")
    private List<TableOrderRequestLine> lines = new ArrayList<>();

    protected TableOrderRequest() {
        // for JPA
    }

    private TableOrderRequest(UUID propertyId, DiningTableId diningTableId, Instant requestedAt) {
        this.id = TableOrderRequestId.newId();
        this.propertyId = propertyId;
        this.diningTableId = diningTableId;
        this.status = TableOrderRequestStatus.PENDING;
        this.requestedAt = requestedAt;
        this.expiresAt = requestedAt.plus(PENDING_WINDOW);
    }

    /**
     * Whether the phone takes an order on a table whose active tab is the one given: yes with no
     * tab or with an {@code OPEN} one, no with a {@code CLOSING} one (decision #9).
     */
    public static boolean acceptsOrdersWith(Optional<Tab> activeTab) {
        Objects.requireNonNull(activeTab, "activeTab");
        return acceptsOrdersWithTabIn(activeTab.map(Tab::status));
    }

    /**
     * Same as {@link #acceptsOrdersWith}, from the status of the active tab alone, for the public
     * read that does not load the tab (decision #40).
     */
    public static boolean acceptsOrdersWithTabIn(Optional<TabStatus> activeTabStatus) {
        Objects.requireNonNull(activeTabStatus, "activeTabStatus");
        return activeTabStatus.map(TabStatus::acceptsItems).orElse(true);
    }

    /**
     * A request sent from the QR code of the table, born {@code PENDING}.
     *
     * <p>Only the cheap checks run here (decision #20), in this order: the tab of the table; how many
     * lines; then line by line the item existing, being on offer, being served at this hour and not
     * being sold by weight, since the customer has no scale. Everything else is checked by
     * {@link Tab#addItem} at confirmation.
     *
     * @param customerName the name the customer typed on the phone (decision #40 of task qr-table-payment)
     * @param customerDeviceId the random code the phone keeps for its device
     * @param activeTab the {@code OPEN} or {@code CLOSING} tab of the table, if any
     * @param menuItems where the item of each line is read from
     * @param propertyZone the zone the availability windows are read in
     * @throws InvalidCustomerNameException if the name is missing, blank or past 40 characters
     * @throws InvalidCustomerDeviceIdException if the device code is missing or malformed
     * @throws TabClosingRefusesOrderRequestException if the tab of the table is {@code CLOSING}
     * @throws InvalidTableOrderRequestException if there is no line, more than 20, or an item sold by weight
     * @throws MenuItemNotFoundException if the item of a line does not exist
     * @throws MenuItemUnavailableException if the item of a line is inactive or ran out
     * @throws MenuItemOutsideAvailabilityWindowException if the schedule does not serve an item now
     */
    public static TableOrderRequest submit(UUID propertyId, DiningTable diningTable, String customerName,
            String customerDeviceId, Optional<Tab> activeTab,
            List<TableOrderRequestLine> lines, MenuItemRepository menuItems, Instant requestedAt,
            ZoneId propertyZone) {
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(diningTable, "diningTable");
        Objects.requireNonNull(menuItems, "menuItems");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(propertyZone, "propertyZone");
        String validCustomerName = requireCustomerName(customerName);
        String validCustomerDeviceId = requireCustomerDeviceId(customerDeviceId);
        if (!acceptsOrdersWith(activeTab)) {
            throw new TabClosingRefusesOrderRequestException("The tab of this dining table is closing");
        }
        if (lines == null || lines.isEmpty() || lines.size() > MAXIMUM_LINES) {
            throw new InvalidTableOrderRequestException(
                    "A request takes from 1 to " + MAXIMUM_LINES + " lines");
        }
        for (TableOrderRequestLine line : lines) {
            requireOrderableByCustomer(menuItems, Objects.requireNonNull(line, "line").menuItemId(),
                    requestedAt, propertyZone);
        }
        TableOrderRequest request = new TableOrderRequest(propertyId, diningTable.id(), requestedAt);
        request.customerName = validCustomerName;
        request.customerDeviceId = validCustomerDeviceId;
        for (int position = 0; position < lines.size(); position++) {
            TableOrderRequestLine line = lines.get(position);
            line.attachTo(request.id, position);
            request.lines.add(line);
        }
        return request;
    }

    /**
     * The name the customer typed, trimmed, 1 to 40 characters (decision #46 of task qr-table-payment).
     *
     * @throws InvalidCustomerNameException if it is missing, blank or too long
     */
    public static String requireCustomerName(String customerName) {
        if (customerName == null || customerName.isBlank()) {
            throw new InvalidCustomerNameException("The customer needs a name");
        }
        String trimmed = customerName.trim();
        if (trimmed.length() > MAXIMUM_CUSTOMER_NAME_LENGTH) {
            throw new InvalidCustomerNameException(
                    "A customer name takes at most " + MAXIMUM_CUSTOMER_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    /**
     * The code of the customer's device: 8 to 64 letters, digits, hyphens or underscores (decision #46).
     *
     * @throws InvalidCustomerDeviceIdException if it is missing or does not match
     */
    public static String requireCustomerDeviceId(String customerDeviceId) {
        if (customerDeviceId == null || !CUSTOMER_DEVICE_ID.matcher(customerDeviceId).matches()) {
            throw new InvalidCustomerDeviceIdException("The customer device code is 8 to 64 letters, digits, - or _");
        }
        return customerDeviceId;
    }

    private static void requireOrderableByCustomer(MenuItemRepository menuItems, MenuItemId menuItemId,
            Instant at, ZoneId propertyZone) {
        MenuItem menuItem = menuItems.findById(menuItemId)
                .orElseThrow(() -> new MenuItemNotFoundException("No menu item " + menuItemId.value()));
        if (!menuItem.isOrderable()) {
            throw new MenuItemUnavailableException("Menu item " + menuItemId.value() + " is unavailable");
        }
        if (!menuItem.isWithinAvailabilityWindowAt(at, propertyZone)) {
            throw new MenuItemOutsideAvailabilityWindowException(
                    "Menu item " + menuItemId.value() + " is not served at this hour");
        }
        if (menuItem.soldByWeight()) {
            throw new InvalidTableOrderRequestException(
                    "Menu item " + menuItemId.value() + " is sold by weight and is ordered at the scale");
        }
    }

    // ------------------------------------------------------------------ resolution

    /**
     * Refuses a request that a waiter can no longer answer: past its window, or already resolved.
     * Changes nothing, so the use case calls it before opening a tab for the request.
     *
     * <p>Expiry is checked first, and the same way whether the job already stored {@code EXPIRED} or
     * the request is still {@code PENDING} past its window (decision #37): the answer never depends on
     * whether the job ran.
     *
     * @throws TableOrderRequestExpiredException if it is stored {@code EXPIRED}, or {@code PENDING}
     *     past its window
     * @throws TableOrderRequestNotPendingException if it was confirmed or rejected
     */
    public void requireAnswerableAt(Instant at) {
        Objects.requireNonNull(at, "at");
        if (status.isExpired() || isExpiredAt(at)) {
            throw new TableOrderRequestExpiredException("Request " + id.value() + " expired at " + expiresAt);
        }
        if (status.isResolved()) {
            throw new TableOrderRequestNotPendingException("Request " + id.value() + " is already " + status);
        }
    }

    /**
     * The waiter put every line on the tab (decision #7).
     *
     * @param tabId the tab the lines went on
     * @throws TableOrderRequestNotPendingException if it was confirmed, rejected or expired
     * @throws TableOrderRequestExpiredException if its window is over
     */
    public void confirm(UUID confirmedBy, Instant confirmedAt, TabId tabId) {
        Objects.requireNonNull(confirmedBy, "confirmedBy");
        Objects.requireNonNull(tabId, "tabId");
        requireAnswerableAt(confirmedAt);
        this.status = TableOrderRequestStatus.CONFIRMED;
        this.resolvedBy = confirmedBy;
        this.resolvedAt = confirmedAt;
        this.tabId = tabId;
    }

    /**
     * The waiter turned the whole request down, with no reason asked (decision #11).
     *
     * @throws TableOrderRequestNotPendingException if it was confirmed, rejected or expired
     * @throws TableOrderRequestExpiredException if its window is over
     */
    public void reject(UUID rejectedBy, Instant rejectedAt) {
        Objects.requireNonNull(rejectedBy, "rejectedBy");
        requireAnswerableAt(rejectedAt);
        this.status = TableOrderRequestStatus.REJECTED;
        this.resolvedBy = rejectedBy;
        this.resolvedAt = rejectedAt;
    }

    /**
     * Nobody answered in time (decision #14). Nobody resolved it either, so no author is recorded.
     *
     * @throws TableOrderRequestNotPendingException if it was confirmed, rejected or expired
     * @throws IllegalStateException if its window is not over yet: only the expiry job and the
     *     submission call this, and both read the overdue requests only
     */
    public void expire(Instant at) {
        Objects.requireNonNull(at, "at");
        if (status.isResolved()) {
            throw new TableOrderRequestNotPendingException("Request " + id.value() + " is already " + status);
        }
        if (!isExpiredAt(at)) {
            throw new IllegalStateException("Request " + id.value() + " is open until " + expiresAt);
        }
        this.status = TableOrderRequestStatus.EXPIRED;
        this.resolvedAt = at;
    }

    // ------------------------------------------------------------------ reading

    /** Still stored as {@code PENDING}, with its window over: from {@link #expiresAt()} on. */
    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return status.isPending() && !now.isBefore(expiresAt);
    }

    /** The status as the customer and the floor read it: {@code EXPIRED} once the window is over. */
    public TableOrderRequestStatus statusAt(Instant now) {
        return isExpiredAt(now) ? TableOrderRequestStatus.EXPIRED : status;
    }

    public TableOrderRequestId id() {
        return id;
    }

    public UUID propertyId() {
        return propertyId;
    }

    public DiningTableId diningTableId() {
        return diningTableId;
    }

    /** The status as stored; read {@link #statusAt} to see a request past its window as expired. */
    public TableOrderRequestStatus status() {
        return status;
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

    /** The tab the lines went on; set only on {@code CONFIRMED}. */
    public Optional<TabId> tabId() {
        return Optional.ofNullable(tabId);
    }

    /** Who sent it from the phone; empty only on a request stored before task qr-table-payment. */
    public Optional<String> customerName() {
        return Optional.ofNullable(customerName);
    }

    /** The device that sent it; never shown to another device. */
    public Optional<String> customerDeviceId() {
        return Optional.ofNullable(customerDeviceId);
    }

    public List<TableOrderRequestLine> lines() {
        return Collections.unmodifiableList(lines);
    }
}
