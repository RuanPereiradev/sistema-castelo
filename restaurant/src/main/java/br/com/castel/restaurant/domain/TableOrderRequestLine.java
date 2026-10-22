package br.com.castel.restaurant.domain;

import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.sharedkernel.AuditedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * One item the customer asked for, as the waiter would order it (decision #19): the item, the
 * variant, how many, the modifiers and the special instructions.
 *
 * <p>A value of the {@link TableOrderRequest}: never changed after it is created. It is mapped as an
 * entity only because its modifiers are a collection of their own, which an embeddable cannot hold
 * (decision #28). Nothing is frozen here; {@link Tab#addItem} freezes name and price at
 * confirmation, with the menu as it is then (decision #13).
 */
@Entity
@Table(name = "table_order_request_line")
public class TableOrderRequestLine extends AuditedEntity {

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private TableOrderRequestLineId id;

    @AttributeOverride(name = "value",
            column = @Column(name = "table_order_request_id", nullable = false, updatable = false))
    private TableOrderRequestId tableOrderRequestId;

    @Column(name = "position", nullable = false, updatable = false)
    private short position;

    @AttributeOverride(name = "value", column = @Column(name = "menu_item_id", nullable = false, updatable = false))
    private MenuItemId menuItemId;

    @AttributeOverride(name = "value", column = @Column(name = "menu_item_variant_id", updatable = false))
    private MenuItemVariantId variantId;

    @Column(name = "quantity", updatable = false)
    private Integer quantity;

    @Column(name = "special_instructions", updatable = false)
    private String specialInstructions;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "table_order_request_line_modifier",
            joinColumns = @JoinColumn(name = "table_order_request_line_id"))
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("position")
    private List<TableOrderRequestLineModifier> modifiers = new ArrayList<>();

    protected TableOrderRequestLine() {
        // for JPA
    }

    /**
     * A line as the customer sent it. Only the item is required; every other value is checked by
     * {@link Tab#addItem} at confirmation.
     *
     * @param variantId the variant chosen, or null
     * @param quantity how many units, or null for 1
     * @param modifiers the modifiers chosen; null is read as none
     * @param specialInstructions free text for the kitchen, or null
     */
    public static TableOrderRequestLine of(MenuItemId menuItemId, MenuItemVariantId variantId, Integer quantity,
            List<TableOrderRequestLineModifier> modifiers, String specialInstructions) {
        TableOrderRequestLine line = new TableOrderRequestLine();
        line.id = TableOrderRequestLineId.newId();
        line.menuItemId = Objects.requireNonNull(menuItemId, "menuItemId");
        line.variantId = variantId;
        line.quantity = quantity;
        List<TableOrderRequestLineModifier> chosen = modifiers == null ? List.of() : modifiers;
        line.modifiers = new ArrayList<>(IntStream.range(0, chosen.size())
                .mapToObj(index -> Objects.requireNonNull(chosen.get(index), "modifier").at(index))
                .toList());
        line.specialInstructions = specialInstructions;
        return line;
    }

    /** Puts the line on its request, at its place in the order the customer sent. */
    void attachTo(TableOrderRequestId request, int requestPosition) {
        this.tableOrderRequestId = Objects.requireNonNull(request, "request");
        this.position = (short) requestPosition;
    }

    public TableOrderRequestLineId id() {
        return id;
    }

    public int position() {
        return position;
    }

    public MenuItemId menuItemId() {
        return menuItemId;
    }

    public Optional<MenuItemVariantId> variantId() {
        return Optional.ofNullable(variantId);
    }

    public Optional<Integer> quantity() {
        return Optional.ofNullable(quantity);
    }

    public List<TableOrderRequestLineModifier> modifiers() {
        return Collections.unmodifiableList(modifiers);
    }

    public Optional<String> specialInstructions() {
        return Optional.ofNullable(specialInstructions);
    }
}
