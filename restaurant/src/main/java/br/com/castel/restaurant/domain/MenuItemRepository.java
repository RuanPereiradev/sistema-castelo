package br.com.castel.restaurant.domain;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.MenuItemId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link MenuItem}. Implemented in {@code restaurant.infra}. */
public interface MenuItemRepository {

    Optional<MenuItem> findById(MenuItemId id);

    /** Whether the property already has an item under this name, ignoring letter case. */
    boolean existsByName(UUID propertyId, String name);

    /** Every item of the property, including the ones out of the menu, in display order. */
    List<MenuItem> findAllOrdered(UUID propertyId);

    /** Only the items still on the menu, in display order — what the public menu is built from. */
    List<MenuItem> findActiveOrdered(UUID propertyId);

    List<MenuItem> findActiveOrderedByCategory(UUID propertyId, MenuCategoryId categoryId);

    MenuItem save(MenuItem item);
}
