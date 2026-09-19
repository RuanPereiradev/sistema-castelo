package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.domain.MenuCategory;
import br.com.castel.restaurant.domain.MenuCategoryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Answers the {@link MenuCategoryRepository} port over Spring Data JPA. */
@Repository
class JpaMenuCategoryRepository implements MenuCategoryRepository {

    private final SpringDataMenuCategoryRepository springData;

    JpaMenuCategoryRepository(SpringDataMenuCategoryRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<MenuCategory> findById(MenuCategoryId id) {
        return springData.findById(id);
    }

    @Override
    public boolean existsByName(UUID propertyId, String name) {
        return springData.existsByName(propertyId, name);
    }

    @Override
    public List<MenuCategory> findAllOrdered(UUID propertyId) {
        return springData.findAllOrdered(propertyId);
    }

    @Override
    public List<MenuCategory> findActiveOrdered(UUID propertyId) {
        return springData.findActiveOrdered(propertyId);
    }

    @Override
    public MenuCategory save(MenuCategory category) {
        return springData.save(category);
    }
}
