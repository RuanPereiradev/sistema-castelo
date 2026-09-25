package br.com.castel.restaurant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link Tab}. Implemented in {@code restaurant.infra}. */
public interface TabRepository {

    Optional<Tab> findById(TabId id);

    /**
     * The tab, with its row locked {@code FOR KEY SHARE} until the transaction ends (decision #15).
     *
     * <p>Two waiters ordering on the same tab take the same lock and do not wait for each other; the
     * {@code FOR UPDATE} of the closing of task 3.2 waits for both, and they wait for it.
     */
    Optional<Tab> findByIdForItemEntry(TabId id);

    /**
     * The tab, with its row locked {@code FOR UPDATE} until the transaction ends, for a change of the
     * tab's own status. It waits for every ordering in progress and they wait for it, so a tab is
     * never cancelled while an item is being added to it (decision #18).
     */
    Optional<Tab> findByIdForStatusChange(TabId id);

    /**
     * Inserts a new tab and flushes, so the partial unique indexes answer now (decision #14).
     *
     * @throws TabAlreadyOpenForDiningTableException if the table already has an active tab
     * @throws TabAlreadyOpenForCardException if the card already has an active tab
     */
    Tab add(Tab tab);

    /** Writes the changes of a tab already stored: a new item, a cancellation. */
    Tab save(Tab tab);

    /**
     * The {@code OPEN} and {@code CLOSING} tabs of the property, by the moment they opened. Each
     * filter that is not null narrows the list.
     */
    List<Tab> findActive(UUID propertyId, DiningTableId diningTableIdOrNull, Integer cardNumberOrNull);

    /** Whether the table has an {@code OPEN} or {@code CLOSING} tab (decision #8). */
    boolean existsActiveOnDiningTable(DiningTableId diningTableId);
}
