package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * A modifier as it went on an item of the tab: its name and price frozen at the moment of the order,
 * and how many of it go on each unit of the item.
 *
 * <p>Part of the {@link Tab} aggregate, stored in {@code tab_item_modifier}. It keeps the id of the
 * {@link Modifier} for reference only: renaming the modifier or changing its price afterwards does not
 * change what the tab charges.
 */
@Embeddable
public class TabItemModifier {

    @AttributeOverride(name = "value", column = @Column(name = "modifier_id", nullable = false))
    private ModifierId modifierId;

    @Column(name = "modifier_name", nullable = false, length = Modifier.MAXIMUM_NAME_LENGTH)
    private String modifierName;

    @Column(name = "price", nullable = false)
    private Money price;

    @Column(name = "quantity", nullable = false)
    private short quantity;

    protected TabItemModifier() {
        // for JPA
    }

    private TabItemModifier(ModifierId modifierId, String modifierName, Money price, short quantity) {
        this.modifierId = modifierId;
        this.modifierName = modifierName;
        this.price = price;
        this.quantity = quantity;
    }

    /** Freezes the modifier as it is now. Receives a quantity the aggregate already validated. */
    static TabItemModifier frozenFrom(Modifier modifier, int validQuantity) {
        return new TabItemModifier(modifier.id(), modifier.name(), modifier.price(), (short) validQuantity);
    }

    /** What this modifier adds to one unit of the item: its price times its quantity. */
    Money totalPerUnit() {
        return price.multiply(quantity);
    }

    public ModifierId modifierId() {
        return modifierId;
    }

    public String modifierName() {
        return modifierName;
    }

    public Money price() {
        return price;
    }

    public int quantity() {
        return quantity;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof TabItemModifier that
                        && modifierId.equals(that.modifierId)
                        && modifierName.equals(that.modifierName)
                        && price.equals(that.price)
                        && quantity == that.quantity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(modifierId, modifierName, price, quantity);
    }
}
