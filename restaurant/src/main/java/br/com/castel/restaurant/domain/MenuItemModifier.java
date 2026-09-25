package br.com.castel.restaurant.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * A modifier offered on an item, with how many of it one unit of the item accepts: "up to 3 extra
 * cheeses".
 *
 * <p>Part of the {@link MenuItem} aggregate. It carries only the id of the {@link Modifier}, never
 * the entity: they are different aggregates, and the name and price are read from the modifier
 * itself.
 *
 * <p>A value: changing the maximum quantity replaces the link instead of mutating it. The table
 * {@code menu_item_modifier} has no audit columns — a link has no history of its own.
 */
@Embeddable
public class MenuItemModifier {

    /** Smallest and largest quantity of one modifier an item can accept. */
    public static final int MINIMUM_QUANTITY = 1;
    public static final int MAXIMUM_QUANTITY = 99;

    @AttributeOverride(name = "value", column = @Column(name = "modifier_id", nullable = false))
    private ModifierId modifierId;

    @Column(name = "max_quantity", nullable = false)
    private short maxQuantity;

    protected MenuItemModifier() {
        // for JPA
    }

    private MenuItemModifier(ModifierId modifierId, short maxQuantity) {
        this.modifierId = modifierId;
        this.maxQuantity = maxQuantity;
    }

    /**
     * @throws InvalidModifierMaxQuantityException if the quantity is outside 1 to 99
     */
    static MenuItemModifier of(ModifierId modifierId, int maxQuantity) {
        Objects.requireNonNull(modifierId, "modifierId");
        if (maxQuantity < MINIMUM_QUANTITY || maxQuantity > MAXIMUM_QUANTITY) {
            throw new InvalidModifierMaxQuantityException(
                    "The maximum quantity of a modifier goes from " + MINIMUM_QUANTITY + " to " + MAXIMUM_QUANTITY);
        }
        return new MenuItemModifier(modifierId, (short) maxQuantity);
    }

    boolean refersTo(ModifierId otherId) {
        return modifierId.equals(otherId);
    }

    public ModifierId modifierId() {
        return modifierId;
    }

    public int maxQuantity() {
        return maxQuantity;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof MenuItemModifier link
                        && modifierId.equals(link.modifierId)
                        && maxQuantity == link.maxQuantity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(modifierId, maxQuantity);
    }
}
