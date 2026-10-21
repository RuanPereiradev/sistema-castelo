package br.com.castel.restaurant.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * A modifier the customer chose on one line of a {@link TableOrderRequest}, with how many of it go
 * on each unit of the item.
 *
 * <p>Only the id and the quantity, as they came: whether the item offers it, whether it is active,
 * whether the quantity fits and whether it came twice is checked by {@link Tab#addItem} at
 * confirmation (decisions #19, #20 and #28), which also freezes its name and price. Stored in
 * {@code table_order_request_line_modifier}, keyed by its position in the line.
 */
@Embeddable
public class TableOrderRequestLineModifier {

    @Column(name = "position", nullable = false, updatable = false)
    private short position;

    @AttributeOverride(name = "value", column = @Column(name = "modifier_id", nullable = false, updatable = false))
    private ModifierId modifierId;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    protected TableOrderRequestLineModifier() {
        // for JPA
    }

    private TableOrderRequestLineModifier(ModifierId modifierId, int quantity) {
        this.modifierId = modifierId;
        this.quantity = quantity;
    }

    /** A modifier as the customer chose it; the quantity is checked only at confirmation. */
    public static TableOrderRequestLineModifier of(ModifierId modifierId, int quantity) {
        return new TableOrderRequestLineModifier(Objects.requireNonNull(modifierId, "modifierId"), quantity);
    }

    /** The same modifier, numbered by its place in the line. */
    TableOrderRequestLineModifier at(int linePosition) {
        TableOrderRequestLineModifier placed = new TableOrderRequestLineModifier(modifierId, quantity);
        placed.position = (short) linePosition;
        return placed;
    }

    public int position() {
        return position;
    }

    public ModifierId modifierId() {
        return modifierId;
    }

    public int quantity() {
        return quantity;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof TableOrderRequestLineModifier that
                        && position == that.position
                        && modifierId.equals(that.modifierId)
                        && quantity == that.quantity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, modifierId, quantity);
    }
}
