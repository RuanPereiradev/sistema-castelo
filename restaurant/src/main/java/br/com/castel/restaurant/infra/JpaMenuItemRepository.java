package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.restaurant.domain.MenuItem;
import br.com.castel.restaurant.domain.MenuItemRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Answers the {@link MenuItemRepository} port over Spring Data JPA. */
@Repository
class JpaMenuItemRepository implements MenuItemRepository {

    private final SpringDataMenuItemRepository springData;

    JpaMenuItemRepository(SpringDataMenuItemRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<MenuItem> findById(MenuItemId id) {
        return springData.findById(id);
    }

    @Override
    public boolean existsByName(UUID propertyId, String name) {
        return springData.existsByName(propertyId, name);
    }

    @Override
    public List<MenuItem> findAllOrdered(UUID propertyId) {
        return springData.findAllOrdered(propertyId);
    }

    @Override
    public List<MenuItem> findActiveOrdered(UUID propertyId) {
        return springData.findActiveOrdered(propertyId);
    }

    @Override
    public List<MenuItem> findActiveOrderedByCategory(UUID propertyId, MenuCategoryId categoryId) {
        return springData.findActiveOrderedByCategory(propertyId, categoryId);
    }

    @Override
    public MenuItem save(MenuItem item) {
        return springData.save(item);
    }
}
