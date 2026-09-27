package br.com.castel.restaurant.application;

import br.com.castel.billing.api.ReceivedPaymentView;
import java.util.Objects;

/** A payment registered through a tab, or the original one on a retry, and the tab's pre-bill now. */
public record TabPaymentView(ReceivedPaymentView payment, TabClosingView closing) {

    public TabPaymentView {
        Objects.requireNonNull(payment, "payment");
        Objects.requireNonNull(closing, "closing");
    }
}
