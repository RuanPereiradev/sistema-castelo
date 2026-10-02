package br.com.castel.restaurant.domain;

import java.util.List;

/**
 * Persistence port for {@link TabItemTransfer}. Implemented in {@code restaurant.infra}.
 *
 * <p>Write only, for now: the trail is kept so that the question "why did table 4 close with less"
 * can be answered, and the report that asks it is out of this task's scope. The read arrives with
 * that report, together with the use case that needs it.
 */
public interface TabItemTransferRepository {

    /**
     * Stores the rows of one move, in the transaction that moved the items. Nothing here takes a
     * lock of its own: the tabs and the items are already locked by the caller, and the insert only
     * needs {@code FOR KEY SHARE} on the rows its foreign keys point at.
     */
    void saveAll(List<TabItemTransfer> transfers);
}
