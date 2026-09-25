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
        List<TabItemResponse> items) {

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
                tab.items().stream().map(TabItemResponse::from).toList());
    }
}
