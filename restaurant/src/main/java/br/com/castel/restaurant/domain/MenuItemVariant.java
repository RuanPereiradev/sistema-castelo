package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.AuditedEntity;
import br.com.castel.sharedkernel.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A size or version of an item with its own price: the small, medium and large pizza.
 *
 * <p>Its price <em>replaces</em> the price of the item (decision #1). Running out
 * ({@link #isAvailable()}) and leaving the menu ({@link #isActive()}) are independent: the large
 * pizza can run out tonight and still be on the menu tomorrow (decision #4).
 *
 * <p>Part of the {@link MenuItem} aggregate, and changed only through it: the rules that involve
 * the other variants, such as a name unique within the item, live there. Like
 * {@link AvailabilityWindow}, it does not map {@code menu_item_id}, which the owning side writes.
 * It is never removed, only deactivated: a tab item of task 2.2 refers to it.
 */
@Entity
@Table(name = "menu_item_variant")
public class MenuItemVariant extends AuditedEntity {

    /** Maximum length of a variant name, matching the column. */
    public static final int MAXIMUM_NAME_LENGTH = 50;

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private MenuItemVariantId id;

    @Column(name = "name", nullable = false, length = MAXIMUM_NAME_LENGTH)
    private String name;

    @Column(name = "unit_price", nullable = false)
    private Money unitPrice;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    protected MenuItemVariant() {
        // for JPA
    }

    private MenuItemVariant(MenuItemVariantId id, String name, Money unitPrice, short displayOrder) {
        this.id = id;
        this.name = name;
        this.unitPrice = unitPrice;
        this.displayOrder = displayOrder;
        this.active = true;
        this.available = true;
    }

    /** Receives a name and a price the aggregate already validated. Born active and available. */
    static MenuItemVariant of(String name, Money unitPrice, int displayOrder) {
        return new MenuItemVariant(MenuItemVariantId.newId(), name, unitPrice, (short) displayOrder);
    }

    void rename(String validName) {
        this.name = validName;
    }

    void changePriceTo(Money validPrice) {
        this.unitPrice = validPrice;
    }

    void markUnavailable() {
        this.available = false;
    }

    void markAvailable() {
        this.available = true;
    }

    void deactivate() {
        this.active = false;
    }

    void activate() {
        this.active = true;
    }

    /** Active and not run out: what a tab can take. */
    boolean isOrderable() {
        return active && available;
    }

    public MenuItemVariantId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    public int displayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isAvailable() {
        return available;
    }
}
