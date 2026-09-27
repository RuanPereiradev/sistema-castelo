package br.com.castel.restaurant.application;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.restaurant.domain.TabItemId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read port of the kitchen display: the tickets of one station, read without loading any
 * {@code Tab} and without any lock. Implemented in {@code restaurant.infra}.
 *
 * <p>Both reads see what the current transaction already wrote.
 */
public interface KitchenQueue {

    /**
     * The tickets on the queue of the station — {@code PENDING}, {@code IN_PREPARATION} and
     * {@code READY} (K5) — whatever the status of their tab (F12), by the moment of the order and
     * then by id.
     */
    List<KitchenTicket> ticketsOf(UUID propertyId, PrepStation station);

    /** The ticket of one item, whatever its status; empty when no tab holds it. */
    Optional<KitchenTicket> ticket(TabItemId itemId);
}
