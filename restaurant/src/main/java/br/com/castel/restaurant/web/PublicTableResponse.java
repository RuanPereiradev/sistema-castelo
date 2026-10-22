package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.TableOrderingView;

/**
 * The table as the phone reads it after scanning the QR code: its label, whether it takes an order
 * now ({@code false} while the tab is {@code CLOSING}, decision #9) and the request still waiting
 * for a waiter, or null. No id of the table, no tab, no total (decision #12).
 */
public record PublicTableResponse(String diningTableLabel, boolean acceptsOrders, String pendingRequestId) {

    public static PublicTableResponse from(TableOrderingView view) {
        return new PublicTableResponse(
                view.diningTable().label(),
                view.acceptsOrders(),
                view.pendingRequestId().map(id -> id.value().toString()).orElse(null));
    }
}
