package br.com.castel.restaurant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link DiningTable}. Implemented in {@code restaurant.infra}. */
public interface DiningTableRepository {

    Optional<DiningTable> findById(DiningTableId id);

    /**
     * The active table printed with this QR code. Empty for a code that never existed, one that was
     * regenerated and one of a deactivated table alike (decision #24).
     */
    Optional<DiningTable> findActiveByOrderingToken(UUID orderingToken);

    /**
     * Locks the row of the table {@code FOR NO KEY UPDATE} until the transaction ends, so two
     * submissions from its QR code count the requests waiting one after the other (decision #64 of
     * task qr-table-payment). Not {@code FOR UPDATE}: opening a tab on the table takes
     * {@code FOR KEY SHARE} through its foreign key, which does not conflict with this lock.
     */
    void lockForOrdering(DiningTableId id);

    /** Whether the property already has a table under this label, ignoring letter case. */
    boolean existsByLabel(UUID propertyId, String label);

    /** Same as {@link #existsByLabel}, leaving out the table being redescribed. */
    boolean existsByLabelExcluding(UUID propertyId, String label, DiningTableId excludedId);

    /**
     * The tables of the property, only the active ones unless {@code includeInactive}. In no
     * particular order: the list order is {@link DiningTable#listingOrder()}.
     */
    List<DiningTable> findAll(UUID propertyId, boolean includeInactive);

    DiningTable save(DiningTable diningTable);
}
