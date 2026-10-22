package br.com.castel.restaurant.application;

import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.TableOrderRequestId;
import java.util.Optional;

/**
 * The table as the phone reads it after scanning the QR code.
 *
 * @param acceptsOrders false while the tab of the table is {@code CLOSING} (decision #9)
 * @param pendingRequestId the request of the table still waiting for a waiter, if any
 */
public record TableOrderingView(
        DiningTable diningTable, boolean acceptsOrders, Optional<TableOrderRequestId> pendingRequestId) {
}
