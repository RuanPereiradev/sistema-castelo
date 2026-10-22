package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.TablePaymentRequestStatusChanged;

/**
 * What {@code /topic/restaurant/table-payment-requests} carries: that a payment of a table changed,
 * and nothing else. The floor reloads its list on it.
 *
 * @param type {@code SUBMITTED}, {@code CONFIRMED}, {@code REJECTED}, {@code EXPIRED} or {@code REFUNDED}
 */
public record TablePaymentRequestMessage(String type, String requestId, String diningTableId, String occurredAt) {

    static final String SUBMITTED = "SUBMITTED";
    static final String REFUNDED = "REFUNDED";

    static TablePaymentRequestMessage of(TablePaymentRequestStatusChanged event) {
        return new TablePaymentRequestMessage(
                typeOf(event),
                event.requestId().value().toString(),
                event.diningTableId().value().toString(),
                event.occurredAt().toString());
    }

    /** A {@code PENDING} payment was just sent, a refunded one was just refunded; any other is its status. */
    private static String typeOf(TablePaymentRequestStatusChanged event) {
        if (event.refunded()) {
            return REFUNDED;
        }
        return event.status().isPending() ? SUBMITTED : event.status().name();
    }
}
