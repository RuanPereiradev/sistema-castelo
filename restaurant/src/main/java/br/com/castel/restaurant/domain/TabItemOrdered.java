package br.com.castel.restaurant.domain;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.DomainEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * An item was ordered on a tab. Published for every item, sold by weight included; whoever shows the
 * kitchen queue looks at the status and drops what is not on it.
 */
public record TabItemOrdered(TabId tabId, TabItemId itemId, PrepStation station, TabItemStatus status,
        Instant occurredAt) implements DomainEvent {

    public TabItemOrdered {
        Objects.requireNonNull(tabId, "tabId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** The item as it was just ordered; the moment is the moment of the order. */
    public static TabItemOrdered of(TabId tabId, TabItem item) {
        return new TabItemOrdered(tabId, item.id(), item.prepStation(), item.status(), item.orderedAt());
    }

    /** Whether the item goes to the kitchen display: an item sold by weight is born delivered. */
    public boolean reachesKitchenQueue() {
        return status.isOnKitchenQueue();
    }
}
