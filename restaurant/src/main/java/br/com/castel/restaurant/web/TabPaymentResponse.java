package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.TabPaymentView;

/**
 * A payment registered through the tab and the pre-bill right after it (decision D10). A retry with
 * the same key answers the original payment and the pre-bill as it is now.
 */
public record TabPaymentResponse(
        String paymentId, String method, String amount, String paidAt, TabBillResponse bill) {

    public static TabPaymentResponse from(TabPaymentView view) {
        return new TabPaymentResponse(
                view.payment().paymentId().value().toString(),
                view.payment().method().name(),
                view.payment().amount().asString(),
                view.payment().paidAt().toString(),
                TabBillResponse.from(view.closing()));
    }
}
