package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.TableOrderingView;

/**
 * The table as the phone reads it after scanning the QR code: its label, whether it takes an order
 * now ({@code false} while the tab is {@code CLOSING}, decision #9, or with 10 requests waiting,
 * decision #66 of task qr-table-payment), the request of the device reading still waiting for a
 * waiter or null, and how many wait on the table. No id of the table, no tab, no total, no device.
 */
public record PublicTableResponse(
        String diningTableLabel, boolean acceptsOrders, String pendingRequestId, int pendingRequestCount) {

    public static PublicTableResponse from(TableOrderingView view) {
        return new PublicTableResponse(
                view.diningTable().label(),
                view.acceptsOrders(),
                view.pendingRequestId().map(id -> id.value().toString()).orElse(null),
                view.pendingRequestCount());
    }
}
