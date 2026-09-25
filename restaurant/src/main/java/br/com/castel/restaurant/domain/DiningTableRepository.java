package br.com.castel.restaurant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link DiningTable}. Implemented in {@code restaurant.infra}. */
public interface DiningTableRepository {

    Optional<DiningTable> findById(DiningTableId id);

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
