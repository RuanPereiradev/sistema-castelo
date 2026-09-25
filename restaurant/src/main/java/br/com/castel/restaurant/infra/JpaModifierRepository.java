package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.Modifier;
import br.com.castel.restaurant.domain.ModifierId;
import br.com.castel.restaurant.domain.ModifierRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Answers the {@link ModifierRepository} port over Spring Data JPA. */
@Repository
class JpaModifierRepository implements ModifierRepository {

    private final SpringDataModifierRepository springData;

    JpaModifierRepository(SpringDataModifierRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<Modifier> findById(ModifierId id) {
        return springData.findById(id);
    }

    @Override
    public boolean existsByName(UUID propertyId, String name) {
        return springData.existsByName(propertyId, name);
    }

    @Override
    public boolean existsByNameExcluding(UUID propertyId, String name, ModifierId excludedId) {
        return springData.existsByNameExcluding(propertyId, name, excludedId);
    }

    @Override
    public List<Modifier> findAllOrdered(UUID propertyId) {
        return springData.findAllOrdered(propertyId);
    }

    @Override
    public Modifier save(Modifier modifier) {
        return springData.save(modifier);
    }
}
