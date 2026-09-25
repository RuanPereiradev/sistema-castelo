package br.com.castel.restaurant.domain;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.AuditedEntity;
import br.com.castel.sharedkernel.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * Something the restaurant sells: a dish, a drink, a plate charged by weight.
 *
 * <p>An item is priced in exactly one of two ways, and the aggregate refuses anything else: either
 * a unit price, or a price per kilo for what the self-service weighs. The database carries the same
 * rule as a {@code CHECK}, so neither an ORM mistake nor a hand-written {@code UPDATE} can leave a
 * row half priced.
 *
 * <p>Availability has two independent parts. {@link #markUnavailable()} is the waiter saying the
 * kitchen ran out, and it wins over everything. The {@link AvailabilityWindow}s are the schedule:
 * an item with no window is served at any hour, and an item with windows is served only inside one
 * of them.
 *
 * <p>An item sold by unit may have {@link MenuItemVariant}s — small, medium, large — each with a
 * price that replaces the price of the item (decision #1). While at least one variant is active the
 * item {@linkplain #requiresVariant() requires} one on the order, is available only when one of them
 * is, and is shown from its {@linkplain #startingPrice() cheapest active variant}. An item sold by
 * weight takes neither variants nor modifiers (decision #3).
 *
 * <p>The {@link MenuItemModifier}s link the item to the {@link Modifier}s it offers. The item keeps
 * only the id of each modifier: a modifier is an aggregate of its own.
 */
@Entity
@Table(name = "menu_item")
public class MenuItem extends AuditedEntity {

    /** Maximum length of an item name, matching the column. */
    public static final int MAXIMUM_NAME_LENGTH = 150;

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private MenuItemId id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @AttributeOverride(name = "value", column = @Column(name = "menu_category_id", nullable = false))
    private MenuCategoryId menuCategoryId;

    @Column(name = "name", nullable = false, length = MAXIMUM_NAME_LENGTH)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "sold_by_weight", nullable = false)
    private boolean soldByWeight;

    @Column(name = "unit_price")
    private Money unitPrice;

    @Column(name = "price_per_kilo")
    private Money pricePerKilo;

    @Enumerated(EnumType.STRING)
    @Column(name = "prep_station", nullable = false, length = 20)
    private PrepStation prepStation;

    @Column(name = "service_charge_eligible", nullable = false)
    private boolean serviceChargeEligible;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private List<AvailabilityWindow> availabilityWindows = new ArrayList<>();

    /*
     * Fetched by subselect: Hibernate refuses to join-fetch more than one list in the same query,
     * and the availability windows already take that place.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "menu_item_id", nullable = false)
    @OrderBy("displayOrder")
    @Fetch(FetchMode.SUBSELECT)
    private List<MenuItemVariant> variants = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "menu_item_modifier", joinColumns = @JoinColumn(name = "menu_item_id"))
    @Fetch(FetchMode.SUBSELECT)
    private List<MenuItemModifier> modifiers = new ArrayList<>();

    protected MenuItem() {
        // for JPA
    }

    private MenuItem(
            MenuItemId id,
            UUID propertyId,
            MenuCategoryId menuCategoryId,
            String name,
            PrepStation prepStation,
            boolean soldByWeight,
            Money unitPrice,
            Money pricePerKilo) {
        this.id = id;
        this.propertyId = propertyId;
        this.menuCategoryId = menuCategoryId;
        this.name = name;
        this.prepStation = prepStation;
        this.soldByWeight = soldByWeight;
        this.unitPrice = unitPrice;
        this.pricePerKilo = pricePerKilo;
        this.serviceChargeEligible = true;
        this.available = true;
        this.active = true;
        this.displayOrder = 0;
    }

    /**
     * An item sold one at a time: a pizza, a beer.
     *
     * @throws InvalidMenuItemPricingException if the price is missing or not positive
     */
    public static MenuItem soldByUnit(
            UUID propertyId, MenuCategoryId categoryId, String name, PrepStation prepStation, Money unitPrice) {
        requireCommonFields(propertyId, categoryId, prepStation);
        return new MenuItem(
                MenuItemId.newId(),
                propertyId,
                categoryId,
                requireValidName(name),
                prepStation,
                false,
                requirePositivePrice(unitPrice, "unit price"),
                null);
    }

    /**
     * An item the self-service weighs and charges by the kilo.
     *
     * @throws InvalidMenuItemPricingException if the price per kilo is missing or not positive
     */
    public static MenuItem soldByWeight(
            UUID propertyId, MenuCategoryId categoryId, String name, PrepStation prepStation, Money pricePerKilo) {
        requireCommonFields(propertyId, categoryId, prepStation);
        return new MenuItem(
                MenuItemId.newId(),
                propertyId,
                categoryId,
                requireValidName(name),
                prepStation,
                true,
                null,
                requirePositivePrice(pricePerKilo, "price per kilo"));
    }

    // ------------------------------------------------------------------ behaviour

    /**
     * Whether the item is served at the given moment, read in the time zone of the property.
     *
     * <p>The moment is absolute and the schedule is local: a window of 18:30 to 23:00 means the
     * clock on the wall of the restaurant, so the caller hands in the zone the property runs on.
     */
    public boolean isAvailableAt(Instant moment, ZoneId propertyZone) {
        Objects.requireNonNull(moment, "moment");
        Objects.requireNonNull(propertyZone, "propertyZone");
        if (!available || !active) {
            return false;
        }
        if (requiresVariant() && variants.stream().noneMatch(MenuItemVariant::isOrderable)) {
            return false;
        }
        if (availabilityWindows.isEmpty()) {
            return true;
        }
        ZonedDateTime local = moment.atZone(propertyZone);
        DayOfWeek day = local.getDayOfWeek();
        LocalTime time = local.toLocalTime();
        return availabilityWindows.stream().anyMatch(window -> window.covers(day, time));
    }

    /**
     * Whether the item is served at the given moment <em>and</em> the variant is active and has not
     * run out.
     *
     * @throws MenuItemVariantNotFoundException if the item has no such variant
     */
    public boolean isVariantAvailableAt(MenuItemVariantId variantId, Instant moment, ZoneId propertyZone) {
        MenuItemVariant variant = variant(variantId);
        return isAvailableAt(moment, propertyZone) && variant.isOrderable();
    }

    /**
     * Whether an order of this item has to name a variant: true while at least one variant is active
     * (decision #2). Deactivating every variant brings the item back to its own price.
     */
    public boolean requiresVariant() {
        return variants.stream().anyMatch(MenuItemVariant::isActive);
    }

    /**
     * The price the menu shows as "from": the cheapest active variant, or the price of the item when
     * it has none. Calculated on every read, never stored, so it cannot drift from the variants.
     */
    public Money startingPrice() {
        return variants.stream()
                .filter(MenuItemVariant::isActive)
                .map(MenuItemVariant::unitPrice)
                .min(Comparator.comparing(Money::amount))
                .orElseGet(this::price);
    }

    /** The kitchen ran out. Overrides the schedule until someone puts it back. */
    public void markUnavailable() {
        this.available = false;
    }

    public void markAvailable() {
        this.available = true;
    }

    /** Out of the menu for good, without losing the history of what was sold. */
    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void rename(String newName) {
        this.name = requireValidName(newName);
    }

    public void describeAs(String newDescription) {
        this.description = newDescription == null || newDescription.isBlank() ? null : newDescription.trim();
    }

    public void moveToCategory(MenuCategoryId newCategoryId) {
        this.menuCategoryId = Objects.requireNonNull(newCategoryId, "newCategoryId");
    }

    public void prepareAt(PrepStation newPrepStation) {
        this.prepStation = Objects.requireNonNull(newPrepStation, "newPrepStation");
    }

    public void moveTo(int newDisplayOrder) {
        this.displayOrder = (short) newDisplayOrder;
    }

    public void chargeServiceCharge(boolean eligible) {
        this.serviceChargeEligible = eligible;
    }

    /**
     * Changes the price, keeping the way the item is sold.
     *
     * @throws InvalidMenuItemPricingException if the price is missing or not positive
     */
    public void changePriceTo(Money newPrice) {
        if (soldByWeight) {
            this.pricePerKilo = requirePositivePrice(newPrice, "price per kilo");
        } else {
            this.unitPrice = requirePositivePrice(newPrice, "unit price");
        }
    }

    /**
     * Adds a stretch of the day in which the item is served.
     *
     * <p>{@code dayOfWeek} null means every day. An item with no window at all is served at any hour.
     */
    public void serveBetween(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        availabilityWindows.add(AvailabilityWindow.of(dayOfWeek, startTime, endTime));
    }

    /** Back to being served at any hour. */
    public void serveAtAnyHour() {
        availabilityWindows.clear();
    }

    // ------------------------------------------------------------------ variants

    /**
     * Adds a variant, active and available, shown after the ones the item already has.
     *
     * @throws SoldByWeightRejectsVariantException if the item is sold by weight
     * @throws InvalidMenuItemVariantNameException if the name is blank or too long for the column
     * @throws InvalidMenuItemPricingException if the price is missing or not positive
     * @throws DuplicateMenuItemVariantNameException if another variant of the item has this name
     */
    public MenuItemVariant addVariant(String name, Money unitPrice) {
        if (soldByWeight) {
            throw new SoldByWeightRejectsVariantException("An item sold by weight takes no variant");
        }
        String validName = requireValidVariantName(name);
        Money validPrice = requirePositivePrice(unitPrice, "variant price");
        rejectDuplicateVariantName(validName, null);
        MenuItemVariant variant = MenuItemVariant.of(validName, validPrice, variants.size());
        variants.add(variant);
        return variant;
    }

    /**
     * @throws MenuItemVariantNotFoundException if the item has no such variant
     * @throws InvalidMenuItemVariantNameException if the name is blank or too long for the column
     * @throws DuplicateMenuItemVariantNameException if another variant of the item has this name
     */
    public void renameVariant(MenuItemVariantId variantId, String newName) {
        MenuItemVariant variant = variant(variantId);
        String validName = requireValidVariantName(newName);
        rejectDuplicateVariantName(validName, variant);
        variant.rename(validName);
    }

    /**
     * @throws MenuItemVariantNotFoundException if the item has no such variant
     * @throws InvalidMenuItemPricingException if the price is missing or not positive
     */
    public void changeVariantPriceTo(MenuItemVariantId variantId, Money newPrice) {
        MenuItemVariant variant = variant(variantId);
        variant.changePriceTo(requirePositivePrice(newPrice, "variant price"));
    }

    /** This variant ran out; the others keep being served (decision #4). */
    public void markVariantUnavailable(MenuItemVariantId variantId) {
        variant(variantId).markUnavailable();
    }

    public void markVariantAvailable(MenuItemVariantId variantId) {
        variant(variantId).markAvailable();
    }

    /** Off the menu. A variant is never removed: a tab item refers to it. */
    public void deactivateVariant(MenuItemVariantId variantId) {
        variant(variantId).deactivate();
    }

    public void activateVariant(MenuItemVariantId variantId) {
        variant(variantId).activate();
    }

    /**
     * @throws MenuItemVariantNotFoundException if the item has no such variant
     */
    public MenuItemVariant variant(MenuItemVariantId variantId) {
        Objects.requireNonNull(variantId, "variantId");
        return variants.stream()
                .filter(variant -> variant.id().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new MenuItemVariantNotFoundException(
                        "Menu item " + id.value() + " has no variant " + variantId.value()));
    }

    // ------------------------------------------------------------------ modifiers

    /**
     * Offers a modifier on this item, up to {@code maxQuantity} per unit. Offering one already
     * offered replaces its maximum quantity instead of linking it twice.
     *
     * @throws SoldByWeightRejectsModifierException if the item is sold by weight
     * @throws InactiveModifierException if the modifier is deactivated
     * @throws InvalidModifierMaxQuantityException if the quantity is outside 1 to 99
     */
    public void offerModifier(Modifier modifier, int maxQuantity) {
        Objects.requireNonNull(modifier, "modifier");
        if (soldByWeight) {
            throw new SoldByWeightRejectsModifierException("An item sold by weight takes no modifier");
        }
        if (!modifier.isActive()) {
            throw new InactiveModifierException("Modifier " + modifier.id().value() + " is deactivated");
        }
        MenuItemModifier link = MenuItemModifier.of(modifier.id(), maxQuantity);
        modifiers.removeIf(existing -> existing.refersTo(modifier.id()));
        modifiers.add(link);
    }

    /**
     * @throws ModifierNotFoundException if the item does not offer this modifier
     */
    public void withdrawModifier(ModifierId modifierId) {
        Objects.requireNonNull(modifierId, "modifierId");
        if (!modifiers.removeIf(existing -> existing.refersTo(modifierId))) {
            throw new ModifierNotFoundException(
                    "Menu item " + id.value() + " does not offer modifier " + modifierId.value());
        }
    }

    // ------------------------------------------------------------------ guards

    private static void requireCommonFields(UUID propertyId, MenuCategoryId categoryId, PrepStation prepStation) {
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(prepStation, "prepStation");
    }

    private static String requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidMenuItemNameException("A menu item needs a name");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAXIMUM_NAME_LENGTH) {
            throw new InvalidMenuItemNameException(
                    "A menu item name takes at most " + MAXIMUM_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String requireValidVariantName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidMenuItemVariantNameException("A variant needs a name");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MenuItemVariant.MAXIMUM_NAME_LENGTH) {
            throw new InvalidMenuItemVariantNameException(
                    "A variant name takes at most " + MenuItemVariant.MAXIMUM_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    /**
     * Unique within the item, ignoring letter case, among active and inactive variants alike: an
     * inactive variant still holds its row, and {@code uk_variant_name} counts it. The variant being
     * renamed does not compete with itself.
     */
    private void rejectDuplicateVariantName(String name, MenuItemVariant renamed) {
        boolean taken = variants.stream()
                .filter(variant -> variant != renamed)
                .anyMatch(variant -> variant.name().equalsIgnoreCase(name));
        if (taken) {
            throw new DuplicateMenuItemVariantNameException("A variant named '" + name + "' already exists");
        }
    }

    private static Money requirePositivePrice(Money price, String what) {
        if (price == null) {
            throw new InvalidMenuItemPricingException("A menu item needs a " + what);
        }
        if (!price.isPositive()) {
            throw new InvalidMenuItemPricingException("A " + what + " must be greater than zero");
        }
        return price;
    }

    // ------------------------------------------------------------------ reading

    public MenuItemId id() {
        return id;
    }

    public UUID propertyId() {
        return propertyId;
    }

    public MenuCategoryId menuCategoryId() {
        return menuCategoryId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public boolean soldByWeight() {
        return soldByWeight;
    }

    /** The price of one unit, or of one kilo when the item is sold by weight. */
    public Money price() {
        return soldByWeight ? pricePerKilo : unitPrice;
    }

    public PrepStation prepStation() {
        return prepStation;
    }

    public boolean serviceChargeEligible() {
        return serviceChargeEligible;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isActive() {
        return active;
    }

    public int displayOrder() {
        return displayOrder;
    }

    public List<AvailabilityWindow> availabilityWindows() {
        return Collections.unmodifiableList(availabilityWindows);
    }

    /** Every variant, active or not, in display order. */
    public List<MenuItemVariant> variants() {
        return variants.stream().sorted(Comparator.comparingInt(MenuItemVariant::displayOrder)).toList();
    }

    /** The modifiers this item offers, whether or not they are active in the catalog. */
    public List<MenuItemModifier> modifiers() {
        return Collections.unmodifiableList(modifiers);
    }
}
