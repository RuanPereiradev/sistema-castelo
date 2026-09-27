package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabItem;
import br.com.castel.sharedkernel.Percentage;

/**
 * One active tab on the list the waiter reads: no items, only how many are active, the subtotal and
 * the total, live while {@code OPEN} and frozen once closing started (decision D20 of task 3.2).
 */
public record TabSummaryResponse(
        String id,
        String origin,
        String status,
        String diningTableId,
        String diningTableLabel,
        Integer cardNumber,
        String openedAt,
        String subtotal,
        long activeItemCount,
        // ---- closing (task 3.2)
        String total) {

    /** @param currentRate the rate of the setting now; used only while no rate is frozen */
    public static TabSummaryResponse from(Tab tab, String diningTableLabel, Percentage currentRate) {
        return new TabSummaryResponse(
                tab.id().value().toString(),
                tab.origin().name(),
                tab.status().name(),
                tab.diningTableId().map(id -> id.value().toString()).orElse(null),
                diningTableLabel,
                tab.cardNumber().orElse(null),
                tab.openedAt().toString(),
                tab.subtotal().asString(),
                tab.items().stream().filter(TabItem::isActive).count(),
                tab.total(currentRate).asString());
    }
}
