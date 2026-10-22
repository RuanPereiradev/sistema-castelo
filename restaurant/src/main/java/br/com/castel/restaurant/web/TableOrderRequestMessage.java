package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.TableOrderRequestStatus;
import br.com.castel.restaurant.domain.TableOrderRequestStatusChanged;

/**
 * What {@code /topic/restaurant/table-order-requests} carries: that a request of a table changed,
 * and nothing else. The floor reloads its list on it (decision #25).
 *
 * @param type {@code SUBMITTED}, {@code CONFIRMED}, {@code REJECTED} or {@code EXPIRED}
 */
public record TableOrderRequestMessage(String type, String requestId, String diningTableId, String occurredAt) {

    static final String SUBMITTED = "SUBMITTED";

    static TableOrderRequestMessage of(TableOrderRequestStatusChanged event) {
        return new TableOrderRequestMessage(
                typeOf(event.status()),
                event.requestId().value().toString(),
                event.diningTableId().value().toString(),
                event.occurredAt().toString());
    }

    /** A request that is still {@code PENDING} was just sent; every other status is its own name. */
    private static String typeOf(TableOrderRequestStatus status) {
        return status.isPending() ? SUBMITTED : status.name();
    }
}
