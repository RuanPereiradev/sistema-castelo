package br.com.castel.restaurant.domain;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.billing.api.ReceivedPaymentView;
import br.com.castel.sharedkernel.AuditedEntity;
import br.com.castel.sharedkernel.EntityId;
import br.com.castel.sharedkernel.Money;
import br.com.castel.sharedkernel.Percentage;
import br.com.castel.sharedkernel.Quantity;
import br.com.castel.sharedkernel.Weight;
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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * What a table or a self-service card consumes, item by item, until the bill is settled.
 *
 * <p>A tab opens on a {@link DiningTable} ({@code TABLE_SERVICE}) or on a self-service card number
 * ({@code SELF_SERVICE}), keeping only the id of the table. At most one {@code OPEN} or
 * {@code CLOSING} tab exists per table and per card (decision #1); that rule is about the set of tabs,
 * so it is held by the partial unique indexes of {@code tab} and not here.
 *
 * <p>Items are appended, never removed: a cancelled item stays with its author, moment and reason
 * (decision #6). Each item freezes the menu as it was at the moment of the order, and
 * {@link #subtotal()} is calculated from the items on every read.
 *
 * <p>Closing (task 3.2) goes in three steps: {@link #startClosing} freezes the service charge rate and
 * posts the total as one {@code TabCharge} on the tab's folio, payments come in through
 * {@link #receivePayment}, and {@link #close} settles the tab once the folio is at zero. In between,
 * {@link #reopen} takes a closing tab back to {@code OPEN}, reversing its charge. The service charge
 * is calculated once over the sum of what counts for it ({@link #serviceCharge}), never item by item.
 *
 * <p>Updated column by column ({@code @DynamicUpdate}, decision D9 of task 3.2): writers under the
 * shared lock of the tab, such as the guest count and the service charge switch, never rewrite each
 * other.
 */
@Entity
@Table(name = "tab")
@DynamicUpdate
public class Tab extends AuditedEntity {

    public static final int MINIMUM_CARD_NUMBER = 1;
    public static final int MAXIMUM_CARD_NUMBER = 999;

    /** Maximum length of the special instructions of an item, after trimming. */
    public static final int MAXIMUM_SPECIAL_INSTRUCTIONS_LENGTH = 200;

    /** Maximum length of a cancellation reason, after trimming. */
    public static final int MAXIMUM_CANCELLATION_REASON_LENGTH = 500;

    /** Order of {@link #items()}: by the moment of the order, then by id so the order is total. */
    private static final Comparator<TabItem> ITEM_ORDER = Comparator
            .comparing(TabItem::orderedAt)
            .thenComparing(item -> item.id().value());

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private TabId id;

    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20, updatable = false)
    private TabOrigin origin;

    @AttributeOverride(name = "value", column = @Column(name = "dining_table_id", updatable = false))
    private DiningTableId diningTableId;

    @Column(name = "card_number", updatable = false)
    private Integer cardNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TabStatus status;

    @Column(name = "public_token", nullable = false, updatable = false)
    private UUID publicToken;

    @Column(name = "opened_by", nullable = false, updatable = false)
    private UUID openedBy;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    /*
     * Fetched by subselect, like the modifiers of each item: Hibernate refuses to join-fetch two
     * lists in one query.
     */
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "tab_id", nullable = false, updatable = false)
    @Fetch(FetchMode.SUBSELECT)
    private List<TabItem> items = new ArrayList<>();

    // ---- closing (task 3.2)

    @Column(name = "folio_id")
    private UUID folioId;

    @Column(name = "tab_charge_id")
    private UUID tabChargeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination", length = 20)
    private TabDestination destination;

    @Column(name = "service_charge_applied", nullable = false)
    private boolean serviceChargeApplied;

    /** The frozen rate, as the fraction {@link Percentage} keeps; null unless closing started. */
    @Column(name = "service_charge_rate", precision = 5, scale = 4)
    private BigDecimal serviceChargeRateFraction;

    @Column(name = "guest_count")
    private Short guestCount;

    @Column(name = "closing_started_at")
    private Instant closingStartedAt;

    @Column(name = "closing_started_by")
    private UUID closingStartedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    // ---- end closing

    protected Tab() {
        // for JPA
    }

    private Tab(UUID propertyId, TabOrigin origin, DiningTableId diningTableId, Integer cardNumber,
            UUID openedBy, Instant openedAt) {
        this.id = TabId.newId();
        this.propertyId = propertyId;
        this.origin = origin;
        this.diningTableId = diningTableId;
        this.cardNumber = cardNumber;
        this.status = TabStatus.OPEN;
        this.publicToken = EntityId.newPublicToken();
        this.openedBy = openedBy;
        this.openedAt = openedAt;
        this.serviceChargeApplied = origin.chargesServiceByDefault();
    }

    /**
     * A table-service tab, open on the given table.
     *
     * @throws InactiveDiningTableException if the table is deactivated
     */
    public static Tab openForTable(UUID propertyId, DiningTable diningTable, UUID openedBy, Instant openedAt) {
        requireOpeningFields(propertyId, openedBy, openedAt);
        Objects.requireNonNull(diningTable, "diningTable");
        if (!diningTable.isActive()) {
            throw new InactiveDiningTableException("Dining table " + diningTable.id().value() + " is deactivated");
        }
        return new Tab(propertyId, TabOrigin.TABLE_SERVICE, diningTable.id(), null, openedBy, openedAt);
    }

    /**
     * A self-service tab, open on the given card. The card is a number, with no registry of its own
     * (decision #2).
     *
     * @throws InvalidCardNumberException if the number falls outside 1 to 999
     */
    public static Tab openForSelfService(UUID propertyId, int cardNumber, UUID openedBy, Instant openedAt) {
        requireOpeningFields(propertyId, openedBy, openedAt);
        if (cardNumber < MINIMUM_CARD_NUMBER || cardNumber > MAXIMUM_CARD_NUMBER) {
            throw new InvalidCardNumberException(
                    "A card number goes from " + MINIMUM_CARD_NUMBER + " to " + MAXIMUM_CARD_NUMBER);
        }
        return new Tab(propertyId, TabOrigin.SELF_SERVICE, null, cardNumber, openedBy, openedAt);
    }

    // ------------------------------------------------------------------ ordering

    /**
     * Orders one item, freezing its price, and appends it to the tab.
     *
     * <p>The rules are checked in this order: the status of the tab; the item being on offer; the
     * schedule; how the item is sold (by weight: variant, modifiers, quantity, weight; by unit:
     * weight, quantity); the variant; the modifiers, choice by choice (repeated, offered, active,
     * quantity); the special instructions.
     *
     * @param propertyZone the zone the availability windows are read in
     * @throws TabNotOpenException if the tab is not {@code OPEN}
     * @throws MenuItemUnavailableException if the item is inactive, ran out, or every variant ran out
     * @throws MenuItemOutsideAvailabilityWindowException if the schedule does not serve it now
     * @throws SoldByWeightRejectsVariantException if an item sold by weight comes with a variant
     * @throws SoldByWeightRejectsQuantityException if an item sold by weight comes with a quantity other than 1
     * @throws SoldByWeightRequiresWeightException if an item sold by weight comes without a weight
     * @throws SoldByUnitRejectsWeightException if an item sold by unit comes with a weight
     * @throws br.com.castel.sharedkernel.InvalidQuantityException if the quantity falls outside 1 to 999
     * @throws br.com.castel.sharedkernel.InvalidWeightException if the weight falls outside 1 to 50000 grams
     * @throws MenuItemVariantRequiredException if the item has active variants and none was named
     * @throws MenuItemVariantNotFoundException if the item has no such variant
     * @throws MenuItemVariantUnavailableException if the variant is inactive or ran out
     * @throws SoldByWeightRejectsModifierException if an item sold by weight comes with modifiers
     * @throws DuplicateTabItemModifierException if the same modifier comes twice
     * @throws ModifierNotOfferedException if the item does not offer a modifier
     * @throws InactiveModifierException if a modifier is deactivated
     * @throws InvalidTabItemModifierQuantityException if a modifier quantity falls outside 1 to its maximum
     * @throws InvalidSpecialInstructionsException if the instructions go past 200 characters
     */
    public TabItem addItem(MenuItem menuItem, TabItemOrder order, UUID orderedBy, Instant orderedAt,
            ZoneId propertyZone) {
        Objects.requireNonNull(menuItem, "menuItem");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(orderedBy, "orderedBy");
        Objects.requireNonNull(orderedAt, "orderedAt");
        Objects.requireNonNull(propertyZone, "propertyZone");
        requireStatusAccepting(status.acceptsItems(), "takes no item");
        if (!menuItem.isOrderable()) {
            throw new MenuItemUnavailableException("Menu item " + menuItem.id().value() + " is unavailable");
        }
        if (!menuItem.isWithinAvailabilityWindowAt(orderedAt, propertyZone)) {
            throw new MenuItemOutsideAvailabilityWindowException(
                    "Menu item " + menuItem.id().value() + " is not served at this hour");
        }
        boolean serviceChargeable = origin.chargesServiceByDefault() && menuItem.serviceChargeEligible();
        TabItem item = menuItem.soldByWeight()
                ? orderByWeight(menuItem, order, serviceChargeable, orderedBy, orderedAt)
                : orderByUnit(menuItem, order, serviceChargeable, orderedBy, orderedAt);
        items.add(item);
        return item;
    }

    private static TabItem orderByWeight(MenuItem menuItem, TabItemOrder order, boolean serviceChargeable,
            UUID orderedBy, Instant orderedAt) {
        if (order.variantId() != null) {
            throw new SoldByWeightRejectsVariantException("An item sold by weight takes no variant");
        }
        if (!order.modifiers().isEmpty()) {
            throw new SoldByWeightRejectsModifierException("An item sold by weight takes no modifier");
        }
        if (order.quantity() != null && order.quantity() != 1) {
            throw new SoldByWeightRejectsQuantityException("An item sold by weight is ordered one plate at a time");
        }
        if (order.weightGrams() == null) {
            throw new SoldByWeightRequiresWeightException("An item sold by weight needs its weight in grams");
        }
        Weight weight = Weight.ofGrams(order.weightGrams());
        String specialInstructions = normaliseSpecialInstructions(order.specialInstructions());
        return TabItem.soldByWeight(menuItem, weight, specialInstructions, serviceChargeable, orderedBy, orderedAt);
    }

    private static TabItem orderByUnit(MenuItem menuItem, TabItemOrder order, boolean serviceChargeable,
            UUID orderedBy, Instant orderedAt) {
        if (order.weightGrams() != null) {
            throw new SoldByUnitRejectsWeightException("An item sold by unit takes no weight");
        }
        Quantity quantity = Quantity.of(order.quantity() == null ? 1 : order.quantity());
        MenuItemVariant variant = requireOrderableVariant(menuItem, order.variantId());
        List<TabItemModifier> modifiers = freezeModifiers(menuItem, order.modifiers());
        String specialInstructions = normaliseSpecialInstructions(order.specialInstructions());
        return TabItem.soldByUnit(menuItem, variant, quantity.value(), modifiers, specialInstructions,
                serviceChargeable, orderedBy, orderedAt);
    }

    /** The variant ordered, or null when the item has no active variant and none was named. */
    private static MenuItemVariant requireOrderableVariant(MenuItem menuItem, MenuItemVariantId variantId) {
        if (variantId == null) {
            if (menuItem.requiresVariant()) {
                throw new MenuItemVariantRequiredException(
                        "Menu item " + menuItem.id().value() + " is ordered by one of its variants");
            }
            return null;
        }
        MenuItemVariant variant = menuItem.variant(variantId);
        if (!variant.isOrderable()) {
            throw new MenuItemVariantUnavailableException("Variant " + variantId.value() + " is unavailable");
        }
        return variant;
    }

    /**
     * A repeated modifier first, over the whole list; then choice by choice, in the order of the list:
     * offered, active, quantity (decision #19).
     */
    private static List<TabItemModifier> freezeModifiers(MenuItem menuItem, List<ModifierChoice> choices) {
        Set<ModifierId> seen = new HashSet<>();
        for (ModifierChoice choice : choices) {
            Modifier modifier = Objects.requireNonNull(choice.modifier(), "modifier");
            if (!seen.add(modifier.id())) {
                throw new DuplicateTabItemModifierException(
                        "Modifier " + modifier.id().value() + " came twice in one order");
            }
        }
        List<TabItemModifier> frozen = new ArrayList<>();
        for (ModifierChoice choice : choices) {
            Modifier modifier = choice.modifier();
            MenuItemModifier link = menuItem.offeredModifier(modifier.id())
                    .orElseThrow(() -> new ModifierNotOfferedException(
                            "Menu item " + menuItem.id().value() + " does not offer modifier " + modifier.id().value()));
            if (!modifier.isActive()) {
                throw new InactiveModifierException("Modifier " + modifier.id().value() + " is deactivated");
            }
            if (choice.quantity() < MenuItemModifier.MINIMUM_QUANTITY || choice.quantity() > link.maxQuantity()) {
                throw new InvalidTabItemModifierQuantityException("Modifier " + modifier.id().value()
                        + " goes from " + MenuItemModifier.MINIMUM_QUANTITY + " to " + link.maxQuantity() + " per unit");
            }
            frozen.add(TabItemModifier.frozenFrom(modifier, choice.quantity()));
        }
        return frozen;
    }

    /** Trimmed; blank is none. */
    private static String normaliseSpecialInstructions(String specialInstructions) {
        if (specialInstructions == null || specialInstructions.isBlank()) {
            return null;
        }
        String trimmed = specialInstructions.trim();
        if (trimmed.length() > MAXIMUM_SPECIAL_INSTRUCTIONS_LENGTH) {
            throw new InvalidSpecialInstructionsException(
                    "Special instructions take at most " + MAXIMUM_SPECIAL_INSTRUCTIONS_LENGTH + " characters");
        }
        return trimmed;
    }

    // ------------------------------------------------------------------ cancelling

    /**
     * Cancels one item, whatever its status except already cancelled, delivered included (decision
     * #6). The item stays on the tab with who cancelled it, when and why.
     *
     * @throws TabNotOpenException if the tab is not {@code OPEN}
     * @throws TabItemNotFoundException if the tab has no such item
     * @throws TabItemAlreadyCancelledException if the item was already cancelled
     * @throws InvalidCancellationReasonException if the reason is missing, blank or past 500 characters
     */
    public void cancelItem(TabItemId itemId, String reason, UUID cancelledBy, Instant cancelledAt) {
        Objects.requireNonNull(cancelledBy, "cancelledBy");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        requireStatusAccepting(status.acceptsItemCancellation(), "has no item cancelled");
        TabItem item = item(itemId);
        item.requireCancellable();
        item.cancel(requireValidReason(reason), cancelledBy, cancelledAt);
    }

    /**
     * Cancels a tab opened by mistake (decision #10), which frees its dining table or card.
     *
     * @throws TabNotOpenException if the tab is not {@code OPEN}
     * @throws TabHasActiveItemsException if any item on it is not cancelled
     * @throws InvalidCancellationReasonException if the reason is missing, blank or past 500 characters
     * <p>For a tab without a folio. The routes cancel through
     * {@link #cancel(String, TabBilling, UUID, Instant)}, which also closes the folio of a tab
     * reopened after its closing started (invariant 17 of task 3.2).
     */
    public void cancel(String reason, UUID cancelledBy, Instant cancelledAt) {
        Objects.requireNonNull(cancelledBy, "cancelledBy");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        requireStatusAccepting(status.acceptsCancellation(), "cannot be cancelled");
        if (items.stream().anyMatch(TabItem::isActive)) {
            throw new TabHasActiveItemsException("Tab " + id.value() + " still has active items");
        }
        this.cancellationReason = requireValidReason(reason);
        this.status = TabStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.cancelledAt = cancelledAt;
    }

    // ---- kitchen display

    /*
     * The transitions of the kitchen display (task 3.5). None of them looks at the status of the tab:
     * preparation is operational, closing is financial, and the kitchen keeps moving an item of a tab
     * that is closing or closed (F12). Each checks, in this order: the item exists, it is not
     * cancelled, its status accepts the transition. Each returns the event of the change.
     */

    /**
     * @throws TabItemNotFoundException if the tab has no such item
     * @throws TabItemAlreadyCancelledException if the item was cancelled
     * @throws InvalidTabItemTransitionException if the item is not {@code PENDING}
     */
    public TabItemStatusChanged startItemPreparation(TabItemId itemId, Instant at) {
        Objects.requireNonNull(at, "at");
        TabItem item = item(itemId);
        TabItemStatus from = item.status();
        item.startPreparation(at);
        return changeOf(item, from, at);
    }

    /**
     * Ready from preparation, or straight from pending (K2).
     *
     * @throws TabItemNotFoundException if the tab has no such item
     * @throws TabItemAlreadyCancelledException if the item was cancelled
     * @throws InvalidTabItemTransitionException if the item is not {@code PENDING} or {@code IN_PREPARATION}
     */
    public TabItemStatusChanged markItemReady(TabItemId itemId, Instant at) {
        Objects.requireNonNull(at, "at");
        TabItem item = item(itemId);
        TabItemStatus from = item.status();
        item.markReady(at);
        return changeOf(item, from, at);
    }

    /**
     * Delivered by the waiter, ready or not (K4).
     *
     * @throws TabItemNotFoundException if the tab has no such item
     * @throws TabItemAlreadyCancelledException if the item was cancelled
     * @throws InvalidTabItemTransitionException if the item was already delivered
     */
    public TabItemStatusChanged deliverItem(TabItemId itemId, Instant at) {
        Objects.requireNonNull(at, "at");
        TabItem item = item(itemId);
        TabItemStatus from = item.status();
        item.deliver(at);
        return changeOf(item, from, at);
    }

    /**
     * One step back, erasing the moment of the step undone (K3). A ready item that skipped
     * preparation goes back to pending.
     *
     * @param at the moment of the undoing, for the event only
     * @throws TabItemNotFoundException if the tab has no such item
     * @throws TabItemAlreadyCancelledException if the item was cancelled
     * @throws InvalidTabItemTransitionException if the item is {@code PENDING} or {@code DELIVERED}
     */
    public TabItemStatusChanged undoItemStatus(TabItemId itemId, Instant at) {
        Objects.requireNonNull(at, "at");
        TabItem item = item(itemId);
        TabItemStatus from = item.status();
        item.undoLastStep();
        return changeOf(item, from, at);
    }

    private TabItemStatusChanged changeOf(TabItem item, TabItemStatus from, Instant at) {
        return new TabItemStatusChanged(id, item.id(), item.prepStation(), from, item.status(), at);
    }

    // ------------------------------------------------------------------ guards

    private static void requireOpeningFields(UUID propertyId, UUID openedBy, Instant openedAt) {
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(openedBy, "openedBy");
        Objects.requireNonNull(openedAt, "openedAt");
    }

    private void requireStatusAccepting(boolean accepted, String refusal) {
        if (!accepted) {
            throw new TabNotOpenException("Tab " + id.value() + " is " + status + " and " + refusal);
        }
    }

    private static String requireValidReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidCancellationReasonException("A cancellation needs a reason");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAXIMUM_CANCELLATION_REASON_LENGTH) {
            throw new InvalidCancellationReasonException(
                    "A cancellation reason takes at most " + MAXIMUM_CANCELLATION_REASON_LENGTH + " characters");
        }
        return trimmed;
    }

    // ------------------------------------------------------------------ reading

    /** Sum of the line totals of the items not cancelled. Calculated on every read, never stored. */
    public Money subtotal() {
        return items.stream()
                .filter(TabItem::isActive)
                .map(TabItem::lineTotal)
                .reduce(Money.ZERO, Money::plus);
    }

    /**
     * @throws TabItemNotFoundException if the tab has no such item
     */
    public TabItem item(TabItemId itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return items.stream()
                .filter(item -> item.refersTo(itemId))
                .findFirst()
                .orElseThrow(() -> new TabItemNotFoundException(
                        "Tab " + id.value() + " has no item " + itemId.value()));
    }

    /** Every item, cancelled ones included, by the moment of the order and then by id. */
    public List<TabItem> items() {
        return items.stream().sorted(ITEM_ORDER).toList();
    }

    public TabId id() {
        return id;
    }

    public UUID propertyId() {
        return propertyId;
    }

    public TabOrigin origin() {
        return origin;
    }

    public TabStatus status() {
        return status;
    }

    public Optional<DiningTableId> diningTableId() {
        return Optional.ofNullable(diningTableId);
    }

    public Optional<Integer> cardNumber() {
        return Optional.ofNullable(cardNumber);
    }

    /** Random, distinct from the id, for the QR code of version 1.1. Not exposed by the API yet. */
    public UUID publicToken() {
        return publicToken;
    }

    public UUID openedBy() {
        return openedBy;
    }

    public Instant openedAt() {
        return openedAt;
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

    // ------------------------------------------------------------------ closing (task 3.2)

    public static final int MINIMUM_SPLIT_GROUP = 1;
    public static final int MAXIMUM_SPLIT_GROUP = 99;
    public static final int MINIMUM_GUEST_COUNT = 1;
    public static final int MAXIMUM_GUEST_COUNT = 999;

    /** Maximum length of the reason of a reopening, after trimming. */
    public static final int MAXIMUM_REOPENING_REASON_LENGTH = 500;

    /**
     * Takes the service charge off the whole tab. No reason: the charge is optional by law and the
     * author is in the audit columns (decision F2). Repeating it changes nothing.
     *
     * @throws TabNotOpenException if the tab is not {@code OPEN}
     */
    public void removeServiceCharge() {
        requireServiceChargeChange();
        this.serviceChargeApplied = false;
    }

    /**
     * Puts the service charge back on the tab, only where its origin charges it: a self-service tab
     * stays without (decision F3). Repeating it changes nothing.
     *
     * @throws TabNotOpenException if the tab is not {@code OPEN}
     */
    public void restoreServiceCharge() {
        requireServiceChargeChange();
        this.serviceChargeApplied = origin.chargesServiceByDefault();
    }

    /**
     * Takes the service charge off one item. A cancelled item is accepted and left as it is, since it
     * weighs on nothing; repeating it changes nothing.
     *
     * @throws TabNotOpenException if the tab is not {@code OPEN}
     * @throws TabItemNotFoundException if the tab has no such item
     * @throws TabItemNotServiceChargeableException if the item was ordered without the charge
     */
    public void removeServiceChargeFrom(TabItemId itemId) {
        TabItem item = serviceChargeableItem(itemId);
        if (item.isActive()) {
            item.waiveServiceCharge();
        }
    }

    /**
     * Puts the service charge back on one item that was ordered with it. A cancelled item is accepted
     * and left as it is; repeating it changes nothing.
     *
     * @throws TabNotOpenException if the tab is not {@code OPEN}
     * @throws TabItemNotFoundException if the tab has no such item
     * @throws TabItemNotServiceChargeableException if the item was ordered without the charge
     */
    public void restoreServiceChargeTo(TabItemId itemId) {
        TabItem item = serviceChargeableItem(itemId);
        if (item.isActive()) {
            item.restoreServiceCharge();
        }
    }

    /**
     * Moves whole lines to split groups (decision F5). All or nothing: every group number is checked,
     * then every item, and only then is anything moved. It never changes the total nor the charge
     * already posted.
     *
     * @param assignments the split group of each item named; items not named stay where they are
     * @throws TabNotOpenException if the tab is neither {@code OPEN} nor {@code CLOSING}
     * @throws InvalidSplitGroupException if a group is missing or falls outside 1 to 99
     * @throws TabItemNotFoundException if the tab has no such item
     */
    public void assignToSplitGroup(Map<TabItemId, Integer> assignments) {
        Objects.requireNonNull(assignments, "assignments");
        requireStatusAccepting(status.acceptsSplitChange(), "has its bill settled");
        for (Integer splitGroup : assignments.values()) {
            if (splitGroup == null || splitGroup < MINIMUM_SPLIT_GROUP || splitGroup > MAXIMUM_SPLIT_GROUP) {
                throw new InvalidSplitGroupException(
                        "A split group goes from " + MINIMUM_SPLIT_GROUP + " to " + MAXIMUM_SPLIT_GROUP);
            }
        }
        Map<TabItem, Integer> moves = new LinkedHashMap<>();
        assignments.forEach((itemId, splitGroup) -> moves.put(item(itemId), splitGroup));
        moves.forEach(TabItem::assignToSplitGroup);
    }

    /**
     * How many guests share the tab (decision F8): optional, the default of an even split.
     *
     * @throws TabNotOpenException if the tab is neither {@code OPEN} nor {@code CLOSING}
     * @throws InvalidGuestCountException if the count falls outside 1 to 999
     */
    public void recordGuestCount(int guestCount) {
        requireStatusAccepting(status.acceptsSplitChange(), "has its bill settled");
        if (guestCount < MINIMUM_GUEST_COUNT || guestCount > MAXIMUM_GUEST_COUNT) {
            throw new InvalidGuestCountException(
                    "The guests of a tab go from " + MINIMUM_GUEST_COUNT + " to " + MAXIMUM_GUEST_COUNT);
        }
        this.guestCount = (short) guestCount;
    }

    /**
     * Starts the closing: the pre-bill. In this order: the status; an active item; the rate is frozen
     * (decision F1); the folio of the tab is opened, or the one it already has is reused after a
     * reopening; the total is posted on it as one charge (decision F6); the tab goes to
     * {@code CLOSING}, which takes no item, no cancellation and no change of the service charge.
     *
     * @param currentRate the rate of the setting now, frozen here
     * @throws TabNotOpenException if the tab is not {@code OPEN}
     * @throws TabHasNoActiveItemsException if no item on it is active
     */
    public void startClosing(Percentage currentRate, TabBilling billing, UUID startedBy, Instant startedAt) {
        Objects.requireNonNull(currentRate, "currentRate");
        Objects.requireNonNull(billing, "billing");
        Objects.requireNonNull(startedBy, "startedBy");
        Objects.requireNonNull(startedAt, "startedAt");
        requireStatusAccepting(status.acceptsClosing(), "cannot start closing");
        if (items.stream().noneMatch(TabItem::isActive)) {
            throw new TabHasNoActiveItemsException("Tab " + id.value() + " has no active item to charge");
        }
        this.serviceChargeRateFraction = currentRate.fraction();
        if (folioId == null) {
            this.folioId = billing.openFolio(id).value();
        }
        this.tabChargeId = billing.charge(new FolioId(folioId), id, total(currentRate), chargeDescription()).value();
        this.closingStartedBy = startedBy;
        this.closingStartedAt = startedAt;
        this.status = TabStatus.CLOSING;
    }

    /**
     * Registers a payment on the folio of the tab. Every rule of the payment is billing's: the key,
     * the method, the amount, the balance, the cash drawer. A {@code CLOSED} tab delegates too, so a
     * retry answers the original payment and anything new meets {@code FOLIO_CLOSED}.
     *
     * @throws TabNotClosingException if the tab is neither {@code CLOSING} nor {@code CLOSED}
     */
    public ReceivedPaymentView receivePayment(PaymentMethod method, Money amount, String idempotencyKey,
            TabBilling billing) {
        Objects.requireNonNull(billing, "billing");
        requireClosingStep(status.acceptsPayment(), "takes no payment");
        return billing.receivePayment(new FolioId(folioId), method, amount, idempotencyKey);
    }

    /**
     * The waiter's override: back to {@code OPEN}, with a reason (decision F10). The charge in force is
     * reversed with that reason, and the reversal on the folio is the record of who reopened and when.
     * What was paid stays on the folio as credit; the frozen rate is dropped and read again on the next
     * closing, which reuses the folio.
     *
     * @throws TabNotClosingException if the tab is not {@code CLOSING}
     * @throws InvalidReopeningReasonException if the reason is missing, blank or past 500 characters
     */
    public void reopen(String reason, TabBilling billing) {
        Objects.requireNonNull(billing, "billing");
        requireClosingStep(status.acceptsReopening(), "cannot be reopened");
        String validReason = requireValidReopeningReason(reason);
        billing.reverse(new FolioId(folioId), new ChargeId(tabChargeId), validReason);
        this.tabChargeId = null;
        this.serviceChargeRateFraction = null;
        this.closingStartedAt = null;
        this.closingStartedBy = null;
        this.status = TabStatus.OPEN;
    }

    /**
     * Closes the tab for good, paid directly, closing its folio in the same transaction (decision #11
     * of task 1.3). The folio refuses a balance other than zero. Items still being prepared do not
     * hold the closing (decision F12). Frees the dining table or the card.
     *
     * @throws TabNotClosingException if the tab is not {@code CLOSING}
     */
    public void close(TabBilling billing, UUID closedBy, Instant closedAt) {
        Objects.requireNonNull(billing, "billing");
        Objects.requireNonNull(closedBy, "closedBy");
        Objects.requireNonNull(closedAt, "closedAt");
        requireClosingStep(status.acceptsSettlement(), "cannot be closed");
        billing.closeFolio(new FolioId(folioId));
        this.destination = TabDestination.DIRECT_PAYMENT;
        this.closedBy = closedBy;
        this.closedAt = closedAt;
        this.status = TabStatus.CLOSED;
    }

    /**
     * Cancels a tab opened by mistake, as {@link #cancel(String, UUID, Instant)} does, and closes its
     * folio when it has one because it was reopened (invariant 17 of task 3.2). The folio refuses a
     * balance other than zero: a payment already registered is refunded first.
     *
     * @throws TabNotOpenException if the tab is not {@code OPEN}
     * @throws TabHasActiveItemsException if any item on it is not cancelled
     * @throws InvalidCancellationReasonException if the reason is missing, blank or past 500 characters
     */
    public void cancel(String reason, TabBilling billing, UUID cancelledBy, Instant cancelledAt) {
        Objects.requireNonNull(billing, "billing");
        Objects.requireNonNull(cancelledBy, "cancelledBy");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        requireStatusAccepting(status.acceptsCancellation(), "cannot be cancelled");
        if (items.stream().anyMatch(TabItem::isActive)) {
            throw new TabHasActiveItemsException("Tab " + id.value() + " still has active items");
        }
        String validReason = requireValidReason(reason);
        folioId().ifPresent(billing::closeFolio);
        this.cancellationReason = validReason;
        this.status = TabStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.cancelledAt = cancelledAt;
    }

    /**
     * What counts for the service charge: the line totals of the active items ordered with it and not
     * waived, when the tab has it on; zero otherwise. Modifiers are in the line total, so they follow
     * their item (decision #8 of task 1.2).
     */
    public Money serviceChargeBase() {
        return items.stream()
                .filter(item -> serviceChargeApplied && item.countsForServiceCharge())
                .map(TabItem::lineTotal)
                .reduce(Money.ZERO, Money::plus);
    }

    /**
     * The service charge, once over {@link #serviceChargeBase()}, rounded half up to the cent — never
     * item by item.
     *
     * @param currentRate the rate of the setting now; used only while no rate is frozen
     */
    public Money serviceCharge(Percentage currentRate) {
        return serviceChargeBase().percentage(rateInForce(currentRate));
    }

    /** The subtotal plus the service charge. */
    public Money total(Percentage currentRate) {
        return subtotal().plus(serviceCharge(currentRate));
    }

    /** The pre-bill: totals, the rate in force and the split groups. */
    public TabBill bill(Percentage currentRate) {
        Percentage rate = rateInForce(currentRate);
        return TabBill.of(items(), serviceChargeApplied, subtotal(), serviceChargeBase(), rate, serviceCharge(rate));
    }

    /**
     * The total in {@code parts} shares of whole cents, the first ones taking the extra cent.
     *
     * @throws InvalidSplitPartsException if the parts fall outside 1 to 99 or outnumber the cents
     */
    public List<Money> evenSplit(Percentage currentRate, int parts) {
        return bill(currentRate).evenSplit(parts);
    }

    /** Whether the tab carries the service charge at all; off by the operator, or on self-service. */
    public boolean serviceChargeApplied() {
        return serviceChargeApplied;
    }

    /** The rate frozen when closing started; empty while the tab is {@code OPEN}. */
    public Optional<Percentage> serviceChargeRate() {
        return Optional.ofNullable(serviceChargeRateFraction).map(Percentage::ofFraction);
    }

    public Optional<Integer> guestCount() {
        return Optional.ofNullable(guestCount).map(Short::intValue);
    }

    public Optional<FolioId> folioId() {
        return Optional.ofNullable(folioId).map(FolioId::new);
    }

    /** The charge in force on the folio; empty while the tab is {@code OPEN}. */
    public Optional<ChargeId> tabChargeId() {
        return Optional.ofNullable(tabChargeId).map(ChargeId::new);
    }

    public Optional<TabDestination> destination() {
        return Optional.ofNullable(destination);
    }

    public Optional<Instant> closingStartedAt() {
        return Optional.ofNullable(closingStartedAt);
    }

    public Optional<UUID> closingStartedBy() {
        return Optional.ofNullable(closingStartedBy);
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Optional<UUID> closedBy() {
        return Optional.ofNullable(closedBy);
    }

    private Percentage rateInForce(Percentage currentRate) {
        Objects.requireNonNull(currentRate, "currentRate");
        return serviceChargeRate().orElse(currentRate);
    }

    private void requireServiceChargeChange() {
        requireStatusAccepting(status.acceptsServiceChargeChange(), "has its service charge frozen");
    }

    private TabItem serviceChargeableItem(TabItemId itemId) {
        requireServiceChargeChange();
        TabItem item = item(itemId);
        if (!item.serviceChargeable()) {
            throw new TabItemNotServiceChargeableException(
                    "Tab item " + itemId.value() + " was ordered without the service charge");
        }
        return item;
    }

    private void requireClosingStep(boolean accepted, String refusal) {
        if (!accepted) {
            throw new TabNotClosingException("Tab " + id.value() + " is " + status + " and " + refusal);
        }
    }

    private static String requireValidReopeningReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidReopeningReasonException("A reopening needs a reason");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAXIMUM_REOPENING_REASON_LENGTH) {
            throw new InvalidReopeningReasonException(
                    "A reopening reason takes at most " + MAXIMUM_REOPENING_REASON_LENGTH + " characters");
        }
        return trimmed;
    }

    /** Written on the folio, where the operator reads it; English, as every text of the backend. */
    private String chargeDescription() {
        return cardNumber != null
                ? "Tab card " + cardNumber
                : "Tab table " + diningTableId.value();
    }
}
