package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.Tab;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A tab with every item, cancelled ones included, as the waiter reads it after each change.
 *
 * <p>{@code diningTableLabel} is read from the table, so the screen does not need a second request.
 * {@code publicToken} is not here: it belongs to the QR code of version 1.1.
 *
 * <p>{@code serviceChargeRate}, {@code serviceCharge} and {@code total} are the figures frozen when
 * closing started, and null while the tab is {@code OPEN} (decision D1 of task 3.2): the live figures
 * of an open tab are in its pre-bill, {@link TabBillResponse}.
 */
public record TabResponse(
        String id,
        String origin,
        String status,
        String diningTableId,
        String diningTableLabel,
        Integer cardNumber,
        String openedAt,
        String openedBy,
        String subtotal,
        String cancelledAt,
        String cancelledBy,
        String cancellationReason,
        List<TabItemResponse> items,
        // ---- closing (task 3.2)
        boolean serviceChargeApplied,
        String serviceChargeRate,
        String serviceCharge,
        String total,
        Integer guestCount,
        String folioId,
        String closingStartedAt,
        String closingStartedBy,
        String closedAt,
        String closedBy,
        String destination) {

    public static TabResponse from(Tab tab, String diningTableLabel) {
        return new TabResponse(
                tab.id().value().toString(),
                tab.origin().name(),
                tab.status().name(),
                tab.diningTableId().map(id -> id.value().toString()).orElse(null),
                diningTableLabel,
                tab.cardNumber().orElse(null),
                tab.openedAt().toString(),
                tab.openedBy().toString(),
                tab.subtotal().asString(),
                tab.cancelledAt().map(Instant::toString).orElse(null),
                tab.cancelledBy().map(UUID::toString).orElse(null),
                tab.cancellationReason().orElse(null),
                tab.items().stream().map(TabItemResponse::from).toList(),
                tab.serviceChargeApplied(),
                TabBillResponse.percentPoints(tab.serviceChargeRate()),
                tab.serviceChargeRate().map(rate -> tab.serviceCharge(rate).asString()).orElse(null),
                frozenTotal(tab),
                tab.guestCount().orElse(null),
                tab.folioId().map(id -> id.value().toString()).orElse(null),
                tab.closingStartedAt().map(Instant::toString).orElse(null),
                tab.closingStartedBy().map(UUID::toString).orElse(null),
                tab.closedAt().map(Instant::toString).orElse(null),
                tab.closedBy().map(UUID::toString).orElse(null),
                tab.destination().map(Enum::name).orElse(null));
    }

    /** The total with the rate frozen when closing started; null while the tab is {@code OPEN}. */
    static String frozenTotal(Tab tab) {
        return tab.serviceChargeRate().map(rate -> tab.total(rate).asString()).orElse(null);
    }
}
