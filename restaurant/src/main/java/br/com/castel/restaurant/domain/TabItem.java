package br.com.castel.restaurant.domain;

import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.AuditedEntity;
import br.com.castel.sharedkernel.Money;
import br.com.castel.sharedkernel.Weight;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * One line of the tab: what was ordered, by whom and when, and what it costs.
 *
 * <p>Everything the menu said at the moment of the order is frozen here — name of the item and of the
 * variant, unit price or price per kilo, preparation station, and the name and price of each
 * modifier — and {@link #lineTotal()} is calculated once, when the item is ordered, and never again.
 * Changing the menu afterwards does not change the tab.
 *
 * <p>Part of the {@link Tab} aggregate: created and cancelled only through it, which checks every
 * rule before handing validated values here. An item is never removed; a cancelled item stays on the
 * tab with its author, moment and reason.
 *
 * <p>The moments of the kitchen display ({@code preparation_started_at}, {@code ready_at},
 * {@code delivered_at}) are written by its transitions (task 3.5); an item sold by weight is born
 * delivered (decision #9). The columns of the transfer between tabs (task 3.6) point at the last
 * tab the item came from; {@code TabItemTransfer} keeps every hop.
 *
 * <p>Updated column by column ({@code @DynamicUpdate}, decision D9 of task 3.2): moving the item to a
 * split group or waiving its service charge writes only those columns, so it never rewrites the
 * status another transaction just changed. Two writers of the status itself — a cancellation and the
 * kitchen display — are kept apart by the {@code FOR UPDATE} lock on the item's row, not by this.
 */
@Entity
@Table(name = "tab_item")
@DynamicUpdate
public class TabItem extends AuditedEntity {

    /** Every item starts in the first split group; the operator moves it to another. */
    public static final int DEFAULT_SPLIT_GROUP = 1;

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private TabItemId id;

    /*
     * The tab holding the item. Mapped here, and no longer only as the join column of Tab.items,
     * because task 3.6 moves an item between tabs and a collection whose join column is read-only
     * writes nothing (section 7.3 of the spec). One writer for the column: this attribute. The
     * collection keeps the same column read-only, so no redundant UPDATE is added to each order.
     */
    @AttributeOverride(name = "value", column = @Column(name = "tab_id", nullable = false))
    private TabId tabId;

    @AttributeOverride(name = "value", column = @Column(name = "menu_item_id", nullable = false, updatable = false))
    private MenuItemId menuItemId;

    @AttributeOverride(name = "value", column = @Column(name = "menu_item_variant_id", updatable = false))
    private MenuItemVariantId variantId;

    @Column(name = "item_name", nullable = false, updatable = false)
    private String itemName;

    @Column(name = "variant_name", updatable = false)
    private String variantName;

    @Column(name = "quantity", nullable = false, updatable = false)
    private short quantity;

    @Column(name = "weight_grams", updatable = false)
    private Integer weightGrams;

    @Column(name = "unit_price", updatable = false)
    private Money unitPrice;

    @Column(name = "price_per_kilo", updatable = false)
    private Money pricePerKilo;

    @Column(name = "line_total", nullable = false, updatable = false)
    private Money lineTotal;

    @Column(name = "service_chargeable", nullable = false, updatable = false)
    private boolean serviceChargeable;

    @Column(name = "special_instructions", updatable = false)
    private String specialInstructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "prep_station", nullable = false, length = 20, updatable = false)
    private PrepStation prepStation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TabItemStatus status;

    @Column(name = "ordered_by", nullable = false, updatable = false)
    private UUID orderedBy;

    @Column(name = "ordered_at", nullable = false, updatable = false)
    private Instant orderedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    // ---- kitchen display
    @Column(name = "preparation_started_at")
    private Instant preparationStartedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    /*
     * Fetched by subselect: the items of the tab are a list already, and Hibernate refuses to
     * join-fetch two lists in one query. Ordered by name, so the response lists them the same way
     * every time.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tab_item_modifier", joinColumns = @JoinColumn(name = "tab_item_id"))
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("modifierName")
    private List<TabItemModifier> modifiers = new ArrayList<>();

    // ---- closing (task 3.2)

    @Column(name = "split_group", nullable = false)
    private short splitGroup = DEFAULT_SPLIT_GROUP;

    @Column(name = "service_charge_waived", nullable = false)
    private boolean serviceChargeWaived;

    // ---- end closing

    // ---- transfer and merge (task 3.6)

    /*
     * The shortcut to the LAST tab the item came from, read by the screen and by the pre-bill
     * without a join, and overwritten on every move. The whole trail, hop by hop, is in
     * tab_item_transfer (decision T11).
     */
    @AttributeOverride(name = "value", column = @Column(name = "transferred_from_tab_id"))
    private TabId transferredFromTabId;

    @Column(name = "transferred_at")
    private Instant transferredAt;

    @Column(name = "transferred_by")
    private UUID transferredBy;

    // ---- end transfer and merge

    protected TabItem() {
        // for JPA
    }

    private TabItem(MenuItem menuItem, boolean serviceChargeable, UUID orderedBy, Instant orderedAt) {
        this.id = TabItemId.newId();
        this.menuItemId = menuItem.id();
        this.itemName = menuItem.name();
        this.prepStation = menuItem.prepStation();
        this.serviceChargeable = serviceChargeable;
        this.orderedBy = orderedBy;
        this.orderedAt = orderedAt;
        this.quantity = 1;
    }

    /**
     * An item sold by unit, pending for its preparation station. The line total is the unit price
     * plus every modifier times its quantity, all times the quantity of the item.
     *
     * <p>Receives values the {@link Tab} already validated.
     */
    static TabItem soldByUnit(
            MenuItem menuItem,
            MenuItemVariant variant,
            int validQuantity,
            List<TabItemModifier> frozenModifiers,
            String validSpecialInstructions,
            boolean serviceChargeable,
            UUID orderedBy,
            Instant orderedAt) {
        TabItem item = new TabItem(menuItem, serviceChargeable, orderedBy, orderedAt);
        item.variantId = variant == null ? null : variant.id();
        item.variantName = variant == null ? null : variant.name();
        item.unitPrice = variant == null ? menuItem.price() : variant.unitPrice();
        item.quantity = (short) validQuantity;
        item.modifiers.addAll(frozenModifiers);
        item.specialInstructions = validSpecialInstructions;
        item.lineTotal = frozenModifiers.stream()
                .map(TabItemModifier::totalPerUnit)
                .reduce(item.unitPrice, Money::plus)
                .multiply(validQuantity);
        item.status = TabItemStatus.PENDING;
        return item;
    }

    /**
     * A plate sold by weight: the customer served themselves, so it is born delivered and never
     * reaches the kitchen display (decision #9). The line total is the weight at the price per kilo,
     * rounded half up to the cent (decision #7).
     *
     * <p>Receives values the {@link Tab} already validated.
     */
    static TabItem soldByWeight(
            MenuItem menuItem,
            Weight weight,
            String validSpecialInstructions,
            boolean serviceChargeable,
            UUID orderedBy,
            Instant orderedAt) {
        TabItem item = new TabItem(menuItem, serviceChargeable, orderedBy, orderedAt);
        item.weightGrams = weight.grams();
        item.pricePerKilo = menuItem.price();
        item.lineTotal = weight.priceAt(item.pricePerKilo);
        item.specialInstructions = validSpecialInstructions;
        item.status = TabItemStatus.DELIVERED;
        item.deliveredAt = orderedAt;
        return item;
    }

    /**
     * @throws TabItemAlreadyCancelledException if the item was already cancelled; the first author
     *     and reason stay
     */
    void requireCancellable() {
        if (!status.acceptsCancellation()) {
            throw new TabItemAlreadyCancelledException("Tab item " + id.value() + " is already cancelled");
        }
    }

    /**
     * Records the cancellation. Receives a reason the {@link Tab} already validated.
     *
     * @throws TabItemAlreadyCancelledException if the item was already cancelled
     */
    void cancel(String validReason, UUID cancelledBy, Instant cancelledAt) {
        requireCancellable();
        this.status = TabItemStatus.CANCELLED;
        this.cancellationReason = validReason;
        this.cancelledBy = cancelledBy;
        this.cancelledAt = cancelledAt;
    }

    boolean refersTo(TabItemId otherId) {
        return id.equals(otherId);
    }

    // ---- kitchen display

    /**
     * @throws TabItemAlreadyCancelledException if the item was cancelled
     * @throws InvalidTabItemTransitionException if the item is not {@code PENDING}
     */
    void startPreparation(Instant at) {
        requireTransition(status.acceptsPreparationStart(), TabItemStatus.IN_PREPARATION);
        this.status = TabItemStatus.IN_PREPARATION;
        this.preparationStartedAt = at;
    }

    /**
     * Ready from preparation, or straight from pending (K2), which leaves no preparation start.
     *
     * @throws TabItemAlreadyCancelledException if the item was cancelled
     * @throws InvalidTabItemTransitionException if the item is not {@code PENDING} or {@code IN_PREPARATION}
     */
    void markReady(Instant at) {
        requireTransition(status.acceptsReady(), TabItemStatus.READY);
        this.status = TabItemStatus.READY;
        this.readyAt = at;
    }

    /**
     * Delivered from any status still on the queue (K4); the moments already recorded stay.
     *
     * @throws TabItemAlreadyCancelledException if the item was cancelled
     * @throws InvalidTabItemTransitionException if the item was already delivered
     */
    void deliver(Instant at) {
        requireTransition(status.acceptsDelivery(), TabItemStatus.DELIVERED);
        this.status = TabItemStatus.DELIVERED;
        this.deliveredAt = at;
    }

    /**
     * Back to the status before the last tap, erasing its moment (K3): {@code IN_PREPARATION} goes
     * back to {@code PENDING}; {@code READY} goes back to {@code IN_PREPARATION} when preparation was
     * started, or to {@code PENDING} when ready skipped it (K2), so an item is never in preparation
     * without the moment it started.
     *
     * @throws TabItemAlreadyCancelledException if the item was cancelled
     * @throws InvalidTabItemTransitionException if the item is {@code PENDING} or {@code DELIVERED}
     */
    void undoLastStep() {
        requireNotCancelled();
        if (!status.acceptsUndo()) {
            throw new InvalidTabItemTransitionException(
                    "Tab item " + id.value() + " is " + status + " and has no step to undo");
        }
        this.status = status.undoneTo(preparationStartedAt != null);
        this.readyAt = null;
        if (!status.carriesPreparationStart()) {
            this.preparationStartedAt = null;
        }
    }

    private void requireTransition(boolean accepted, TabItemStatus target) {
        requireNotCancelled();
        if (!accepted) {
            throw new InvalidTabItemTransitionException(
                    "Tab item " + id.value() + " cannot go from " + status + " to " + target);
        }
    }

    private void requireNotCancelled() {
        if (!status.isActive()) {
            throw new TabItemAlreadyCancelledException("Tab item " + id.value() + " is cancelled");
        }
    }

    public Optional<Instant> preparationStartedAt() {
        return Optional.ofNullable(preparationStartedAt);
    }

    public Optional<Instant> readyAt() {
        return Optional.ofNullable(readyAt);
    }

    /** Shown on the kitchen display of its station: pending, in preparation or ready (K5). */
    public boolean isOnKitchenQueue() {
        return status.isOnKitchenQueue();
    }

    // ------------------------------------------------------------------ reading

    public TabItemId id() {
        return id;
    }

    public MenuItemId menuItemId() {
        return menuItemId;
    }

    public Optional<MenuItemVariantId> variantId() {
        return Optional.ofNullable(variantId);
    }

    public String itemName() {
        return itemName;
    }

    public Optional<String> variantName() {
        return Optional.ofNullable(variantName);
    }

    public int quantity() {
        return quantity;
    }

    public Optional<Weight> weight() {
        return Optional.ofNullable(weightGrams).map(Weight::ofGrams);
    }

    public Optional<Money> unitPrice() {
        return Optional.ofNullable(unitPrice);
    }

    public Optional<Money> pricePerKilo() {
        return Optional.ofNullable(pricePerKilo);
    }

    public List<TabItemModifier> modifiers() {
        return Collections.unmodifiableList(modifiers);
    }

    public Money lineTotal() {
        return lineTotal;
    }

    public boolean serviceChargeable() {
        return serviceChargeable;
    }

    public Optional<String> specialInstructions() {
        return Optional.ofNullable(specialInstructions);
    }

    public PrepStation prepStation() {
        return prepStation;
    }

    public TabItemStatus status() {
        return status;
    }

    public UUID orderedBy() {
        return orderedBy;
    }

    public Instant orderedAt() {
        return orderedAt;
    }

    public Optional<Instant> deliveredAt() {
        return Optional.ofNullable(deliveredAt);
    }

    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    public Optional<UUID> cancelledBy() {
        return Optional.ofNullable(cancelledBy);
    }

    public Optional<String> cancellationReason() {
        return Optional.ofNullable(cancellationReason);
    }

    /** Not cancelled: counts in the subtotal and keeps the tab from being cancelled. */
    public boolean isActive() {
        return status.isActive();
    }

    // ------------------------------------------------------------------ closing (task 3.2)

    /** Takes the service charge off this item. Receives an item the {@link Tab} already checked. */
    void waiveServiceCharge() {
        this.serviceChargeWaived = true;
    }

    /** Puts the service charge back on this item. Receives an item the {@link Tab} already checked. */
    void restoreServiceCharge() {
        this.serviceChargeWaived = false;
    }

    /** Moves the whole line to a split group the {@link Tab} already validated. */
    void assignToSplitGroup(int validSplitGroup) {
        this.splitGroup = (short) validSplitGroup;
    }

    /** Whether the operator took the service charge off this item; it can be put back. */
    public boolean serviceChargeWaived() {
        return serviceChargeWaived;
    }

    /** The split group the whole line belongs to, 1 to 99. */
    public int splitGroup() {
        return splitGroup;
    }

    /**
     * Whether the line total counts for the service charge: active, ordered with the charge, and not
     * waived by the operator. The modifiers are in the line total, so they follow the item (decision
     * #8 of task 1.2).
     */
    public boolean countsForServiceCharge() {
        return isActive() && serviceChargeable && !serviceChargeWaived;
    }

    // ------------------------------------------------------------------ transfer and merge (task 3.6)

    /** Which tab holds the item right now. Set when it is ordered and again whenever it moves. */
    void attachTo(TabId holdingTab) {
        this.tabId = Objects.requireNonNull(holdingTab, "holdingTab");
    }

    /**
     * Moves the whole line to another tab. Receives an item the {@link Tab} already checked: both
     * tabs {@code OPEN}, the item not cancelled.
     *
     * <p>Nothing frozen is touched — the line total, the prices, the modifiers,
     * {@code serviceChargeable}, the station, the status and the moments of the kitchen display all
     * travel as they are. What changes is where the line belongs:
     *
     * <ul>
     *   <li>{@code tabId}, to the destination;
     *   <li>the shortcut to where it came from, overwriting the previous hop (decision T11);
     *   <li>the split group, back to the first one, because group 2 of table 4 is not group 2 of
     *       table 5 and keeping the number would put the line on the bill of unrelated people
     *       (decision T9);
     *   <li>the waived service charge, which is only ever turned on here: an item leaving a tab whose
     *       charge is off arrives waived, so its effective value does not change with nobody deciding
     *       it (decision T7). The waiter of the destination turns it back on if they want.
     * </ul>
     *
     * @param waiveServiceCharge whether the charge has to be waived on arrival; never un-waives
     */
    void transferTo(TabId destination, TabId source, boolean waiveServiceCharge, UUID by, Instant at) {
        attachTo(destination);
        this.transferredFromTabId = Objects.requireNonNull(source, "source");
        this.transferredBy = Objects.requireNonNull(by, "by");
        this.transferredAt = Objects.requireNonNull(at, "at");
        this.splitGroup = DEFAULT_SPLIT_GROUP;
        if (waiveServiceCharge) {
            this.serviceChargeWaived = true;
        }
    }

    /** The tab holding the item. */
    public TabId tabId() {
        return tabId;
    }

    /** The last tab the item came from; empty while it never moved. */
    public Optional<TabId> transferredFromTabId() {
        return Optional.ofNullable(transferredFromTabId);
    }

    public Optional<Instant> transferredAt() {
        return Optional.ofNullable(transferredAt);
    }

    public Optional<UUID> transferredBy() {
        return Optional.ofNullable(transferredBy);
    }
}
