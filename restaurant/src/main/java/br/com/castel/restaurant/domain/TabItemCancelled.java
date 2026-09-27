package br.com.castel.restaurant.domain;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.DomainEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * An item of a tab was cancelled, whatever status it was in (decision #6). Carries the reason, so the
 * kitchen display can show it next to the struck-out ticket (K9).
 */
public record TabItemCancelled(TabId tabId, TabItemId itemId, PrepStation station, String reason,
        Instant occurredAt) implements DomainEvent {

    public TabItemCancelled {
        Objects.requireNonNull(tabId, "tabId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /**
     * The item just cancelled, with its reason and moment.
     *
     * @throws java.util.NoSuchElementException if the item is not cancelled
     */
    public static TabItemCancelled of(TabId tabId, TabItem cancelledItem) {
        return new TabItemCancelled(
                tabId,
                cancelledItem.id(),
                cancelledItem.prepStation(),
                cancelledItem.cancellationReason().orElseThrow(),
                cancelledItem.cancelledAt().orElseThrow());
    }
}
