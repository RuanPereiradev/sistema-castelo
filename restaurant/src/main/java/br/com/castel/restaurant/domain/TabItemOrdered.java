package br.com.castel.restaurant.domain;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.DomainEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * An item was ordered on a tab. Published for every item, sold by weight and needing no preparation
 * included; whoever shows the kitchen queue asks {@link #reachesKitchenQueue()} and drops the rest.
 */
public record TabItemOrdered(TabId tabId, TabItemId itemId, PrepStation station, TabItemStatus status,
        boolean requiresPreparation, Instant occurredAt) implements DomainEvent {

    public TabItemOrdered {
        Objects.requireNonNull(tabId, "tabId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** The item as it was just ordered; the moment is the moment of the order. */
    public static TabItemOrdered of(TabId tabId, TabItem item) {
        return new TabItemOrdered(tabId, item.id(), item.prepStation(), item.status(), item.requiresPreparation(),
                item.orderedAt());
    }

    /**
     * Whether the item goes to the kitchen display: an item sold by weight is born delivered, and an
     * item that needs no preparation is born ready, off the display.
     */
    public boolean reachesKitchenQueue() {
        return requiresPreparation && status.isOnKitchenQueue();
    }

    /** Born ready, because it needs no preparation: what the waiters are told about (K10). */
    public boolean isReady() {
        return status == TabItemStatus.READY;
    }
}
