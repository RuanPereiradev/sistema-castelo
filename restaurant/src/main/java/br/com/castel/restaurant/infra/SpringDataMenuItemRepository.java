package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.restaurant.domain.MenuItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data's view of {@code menu_item}, used only by {@link JpaMenuItemRepository}. */
interface SpringDataMenuItemRepository extends JpaRepository<MenuItem, MenuItemId> {

    @Query("select count(i) > 0 from MenuItem i where i.propertyId = :propertyId and lower(i.name) = lower(:name)")
    boolean existsByName(@Param("propertyId") UUID propertyId, @Param("name") String name);

    @Query("select i from MenuItem i where i.propertyId = :propertyId order by i.displayOrder, i.name")
    List<MenuItem> findAllOrdered(@Param("propertyId") UUID propertyId);

    @Query("select i from MenuItem i where i.propertyId = :propertyId and i.active = true "
            + "order by i.displayOrder, i.name")
    List<MenuItem> findActiveOrdered(@Param("propertyId") UUID propertyId);

    @Query("select i from MenuItem i where i.propertyId = :propertyId and i.active = true "
            + "and i.menuCategoryId = :categoryId order by i.displayOrder, i.name")
    List<MenuItem> findActiveOrderedByCategory(
            @Param("propertyId") UUID propertyId, @Param("categoryId") MenuCategoryId categoryId);
}
