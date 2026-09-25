package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.AuditedEntity;
import br.com.castel.sharedkernel.EntityId;
import br.com.castel.sharedkernel.Money;
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
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 * <p>Closing, the service charge and the total arrive with task 3.2.
 */
@Entity
@Table(name = "tab")
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

    /** Choice by choice, in the order of the list: repeated, offered, active, quantity. */
    private static List<TabItemModifier> freezeModifiers(MenuItem menuItem, List<ModifierChoice> choices) {
        Set<ModifierId> seen = new HashSet<>();
        List<TabItemModifier> frozen = new ArrayList<>();
        for (ModifierChoice choice : choices) {
            Modifier modifier = Objects.requireNonNull(choice.modifier(), "modifier");
            if (!seen.add(modifier.id())) {
                throw new DuplicateTabItemModifierException(
                        "Modifier " + modifier.id().value() + " came twice in one order");
            }
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
}
