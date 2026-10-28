package br.com.castel.restaurant.application;

import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.TableOrderRequestId;
import java.util.Optional;

/**
 * The table as the phone reads it after scanning the QR code.
 *
 * @param acceptsOrders false while the tab of the table is {@code CLOSING} (decision #9), or while
 *     the table has 10 requests waiting (decision #66 of task qr-table-payment)
 * @param pendingRequestId the request of the device reading still waiting for a waiter, if any;
 *     empty when the phone did not say which device it is
 * @param pendingRequestCount how many requests of the table wait for a waiter
 */
public record TableOrderingView(
        DiningTable diningTable,
        boolean acceptsOrders,
        Optional<TableOrderRequestId> pendingRequestId,
        int pendingRequestCount) {
}
