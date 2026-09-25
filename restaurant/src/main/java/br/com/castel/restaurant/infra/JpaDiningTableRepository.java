package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.DiningTableRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Answers the {@link DiningTableRepository} port over Spring Data JPA. */
@Repository
class JpaDiningTableRepository implements DiningTableRepository {

    private final SpringDataDiningTableRepository springData;

    JpaDiningTableRepository(SpringDataDiningTableRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<DiningTable> findById(DiningTableId id) {
        return springData.findById(id);
    }

    @Override
    public boolean existsByLabel(UUID propertyId, String label) {
        return springData.existsByLabel(propertyId, label);
    }

    @Override
    public boolean existsByLabelExcluding(UUID propertyId, String label, DiningTableId excludedId) {
        return springData.existsByLabelExcluding(propertyId, label, excludedId);
    }

    @Override
    public List<DiningTable> findAll(UUID propertyId, boolean includeInactive) {
        return springData.findAll(propertyId, includeInactive);
    }

    @Override
    public DiningTable save(DiningTable diningTable) {
        return springData.save(diningTable);
    }
}
