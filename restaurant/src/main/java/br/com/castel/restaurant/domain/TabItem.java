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
import java.util.Optional;
import java.util.UUID;
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
 * <p>The columns of the kitchen display (task 3.5) and of the transfer between tabs (task 3.6) exist
 * in {@code tab_item} but are not mapped yet, except {@code delivered_at}: an item sold by weight is
 * born delivered (decision #9).
 */
@Entity
@Table(name = "tab_item")
public class TabItem extends AuditedEntity {

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private TabItemId id;

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
}
