package br.com.castel.restaurant.domain;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.DomainEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * An item moved from one tab to another. Published once per item moved, whether by a transfer, a
 * merge or a table move.
 *
 * <p>The kitchen display cares because the ticket shows the table of the tab: the dish must not
 * vanish from the queue nor stay there labelled with the table it left.
 */
public record TabItemTransferred(TabId fromTabId, TabId toTabId, TabItemId itemId, PrepStation station,
        TabItemStatus status, Instant occurredAt) implements DomainEvent {

    public TabItemTransferred {
        Objects.requireNonNull(fromTabId, "fromTabId");
        Objects.requireNonNull(toTabId, "toTabId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** The item as it stands right after the move; the moment is the moment of the move. */
    static TabItemTransferred of(TabId fromTabId, TabId toTabId, TabItem item, Instant at) {
        return new TabItemTransferred(fromTabId, toTabId, item.id(), item.prepStation(), item.status(), at);
    }

    /** Whether the item is on the kitchen display of its station, so the screen has to be told. */
    public boolean reachesKitchenQueue() {
        return status.isOnKitchenQueue();
    }

    /** Whether the item is waiting to be picked up, which the waiters are told about too (K10). */
    public boolean isReady() {
        return status == TabItemStatus.READY;
    }
}
