package br.com.castel.restaurant.domain;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.DomainEvent;
import java.time.Instant;
import java.util.Objects;

/** The kitchen display moved an item from one status to another. Returned by the {@link Tab}. */
public record TabItemStatusChanged(TabId tabId, TabItemId itemId, PrepStation station,
        TabItemStatus from, TabItemStatus to, Instant occurredAt) implements DomainEvent {

    public TabItemStatusChanged {
        Objects.requireNonNull(tabId, "tabId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** Whether the item entered or left {@code READY}: what the waiters are told about (K10). */
    public boolean touchesReady() {
        return from == TabItemStatus.READY || to == TabItemStatus.READY;
    }
}
