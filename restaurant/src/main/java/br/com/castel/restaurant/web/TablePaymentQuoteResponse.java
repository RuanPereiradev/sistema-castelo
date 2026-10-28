package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.Tab;
import java.util.List;

/**
 * What a payment of the units chosen would cost (decision #55 of task qr-table-payment): computed by
 * the same code as the real request, which is the authority if the tab changes in between.
 */
public record TablePaymentQuoteResponse(String subtotal, String serviceCharge, String amount, List<Item> items) {

    /** Units of one item, with their value and share of the charge. */
    public record Item(String tabItemId, int quantity, String lineTotal, String serviceCharge) {
    }

    static TablePaymentQuoteResponse from(Tab.PaymentPart part) {
        return new TablePaymentQuoteResponse(
                part.subtotal().asString(),
                part.serviceCharge().asString(),
                part.amount().asString(),
                part.items().stream()
                        .map(item -> new Item(item.tabItemId().value().toString(), item.quantity(),
                                item.lineTotal().asString(), item.serviceCharge().asString()))
                        .toList());
    }
}
