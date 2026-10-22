package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * Units of one item of the tab a {@link TablePaymentRequest} pays, with what they weigh in the amount:
 * how many, their value and their share of the request's service charge, frozen when the request is
 * created (decisions #20, #39, #42 and #45 of task qr-table-payment).
 *
 * <p>Stored in {@code table_payment_request_item} and kept after a rejection, an expiry or a refund:
 * the request always says what it was for.
 */
@Embeddable
public class TablePaymentRequestItem {

    @AttributeOverride(name = "value", column = @Column(name = "tab_item_id", nullable = false, updatable = false))
    private TabItemId tabItemId;

    @Column(name = "quantity", nullable = false, updatable = false)
    private short quantity;

    /** The value of the units paid, not of the whole line. */
    @Column(name = "line_total", nullable = false, updatable = false)
    private Money lineTotal;

    @Column(name = "service_charge", nullable = false, updatable = false)
    private Money serviceCharge;

    protected TablePaymentRequestItem() {
        // for JPA
    }

    private TablePaymentRequestItem(TabItemId tabItemId, int quantity, Money lineTotal, Money serviceCharge) {
        this.tabItemId = tabItemId;
        this.quantity = (short) quantity;
        this.lineTotal = lineTotal;
        this.serviceCharge = serviceCharge;
    }

    /** Built by the {@link Tab}, which valued the units and shared the charge. */
    static TablePaymentRequestItem of(TabItemId tabItemId, int quantity, Money lineTotal, Money serviceCharge) {
        return new TablePaymentRequestItem(
                Objects.requireNonNull(tabItemId, "tabItemId"),
                quantity,
                Objects.requireNonNull(lineTotal, "lineTotal"),
                Objects.requireNonNull(serviceCharge, "serviceCharge"));
    }

    public TabItemId tabItemId() {
        return tabItemId;
    }

    /** How many units of the item. */
    public int quantity() {
        return quantity;
    }

    /** The value of the units paid. */
    public Money lineTotal() {
        return lineTotal;
    }

    /** The item's share of the service charge of the request. */
    public Money serviceCharge() {
        return serviceCharge;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof TablePaymentRequestItem that
                        && tabItemId.equals(that.tabItemId)
                        && quantity == that.quantity
                        && lineTotal.equals(that.lineTotal)
                        && serviceCharge.equals(that.serviceCharge);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tabItemId, quantity, lineTotal, serviceCharge);
    }
}
