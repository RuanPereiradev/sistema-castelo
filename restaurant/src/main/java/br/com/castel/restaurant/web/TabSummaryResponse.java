package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabItem;

/** One active tab on the list the waiter reads: no items, only how many are active and the subtotal. */
public record TabSummaryResponse(
        String id,
        String origin,
        String status,
        String diningTableId,
        String diningTableLabel,
        Integer cardNumber,
        String openedAt,
        String subtotal,
        long activeItemCount) {

    public static TabSummaryResponse from(Tab tab, String diningTableLabel) {
        return new TabSummaryResponse(
                tab.id().value().toString(),
                tab.origin().name(),
                tab.status().name(),
                tab.diningTableId().map(id -> id.value().toString()).orElse(null),
                diningTableLabel,
                tab.cardNumber().orElse(null),
                tab.openedAt().toString(),
                tab.subtotal().asString(),
                tab.items().stream().filter(TabItem::isActive).count());
    }
}
