package br.com.castel.restaurant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link Modifier}. Implemented in {@code restaurant.infra}. */
public interface ModifierRepository {

    Optional<Modifier> findById(ModifierId id);

    /** Whether the property already has a modifier under this name, ignoring letter case. */
    boolean existsByName(UUID propertyId, String name);

    /** Same as {@link #existsByName}, leaving out the modifier being renamed. */
    boolean existsByNameExcluding(UUID propertyId, String name, ModifierId excludedId);

    /** The whole catalog of the property, active or not, by name. */
    List<Modifier> findAllOrdered(UUID propertyId);

    Modifier save(Modifier modifier);
}
