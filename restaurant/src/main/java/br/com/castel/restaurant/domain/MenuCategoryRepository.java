package br.com.castel.restaurant.domain;

import br.com.castel.restaurant.api.MenuCategoryId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link MenuCategory}. Implemented in {@code restaurant.infra}. */
public interface MenuCategoryRepository {

    Optional<MenuCategory> findById(MenuCategoryId id);

    /** Whether the property already has a category under this name, ignoring letter case. */
    boolean existsByName(UUID propertyId, String name);

    /** Every category of the property, in the order the menu shows them. */
    List<MenuCategory> findAllOrdered(UUID propertyId);

    /** Only the categories currently on the menu, in the order it shows them. */
    List<MenuCategory> findActiveOrdered(UUID propertyId);

    MenuCategory save(MenuCategory category);
}
