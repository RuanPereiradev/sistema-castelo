package br.com.castel.restaurant.domain;

import java.util.List;

/** Persistence port for {@link TabItemTransfer}. Implemented in {@code restaurant.infra}. */
public interface TabItemTransferRepository {

    /**
     * Stores the rows of one move, in the transaction that moved the items. Nothing here takes a
     * lock of its own: the tabs and the items are already locked by the caller, and the insert only
     * needs {@code FOR KEY SHARE} on the rows its foreign keys point at.
     */
    void saveAll(List<TabItemTransfer> transfers);

    /** Every movement of one item, oldest first: the trail of all its hops. */
    List<TabItemTransfer> findByTabItemId(TabItemId itemId);
}
