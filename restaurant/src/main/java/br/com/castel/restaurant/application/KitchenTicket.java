package br.com.castel.restaurant.application;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.restaurant.domain.TabItemStatus;
import br.com.castel.restaurant.domain.TabOrigin;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One item of a tab as the kitchen display shows it (K7): where it goes, what to prepare and since
 * when. No price and no waiter. A read model, never loaded through the {@code Tab} aggregate.
 *
 * <p>{@code diningTableLabel} is set for a table-service tab and {@code cardNumber} for a
 * self-service one. {@code variantName}, {@code specialInstructions}, {@code preparationStartedAt}
 * and {@code readyAt} are null when they do not apply. {@code updatedAt} is the last change of the
 * row, for the screen to drop a message older than what it already shows.
 */
public record KitchenTicket(
        TabItemId itemId,
        TabId tabId,
        TabOrigin origin,
        String diningTableLabel,
        Integer cardNumber,
        String itemName,
        String variantName,
        int quantity,
        List<ModifierLine> modifiers,
        String specialInstructions,
        PrepStation prepStation,
        TabItemStatus status,
        Instant orderedAt,
        Instant preparationStartedAt,
        Instant readyAt,
        Instant updatedAt) {

    public KitchenTicket {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(tabId, "tabId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(itemName, "itemName");
        Objects.requireNonNull(prepStation, "prepStation");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(orderedAt, "orderedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        modifiers = List.copyOf(modifiers);
    }

    /** A modifier on the item, by name, with how many go on each unit. */
    public record ModifierLine(String name, int quantity) {

        public ModifierLine {
            Objects.requireNonNull(name, "name");
        }
    }
}
