package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.domain.MenuCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data's view of {@code menu_category}, used only by {@link JpaMenuCategoryRepository}. */
interface SpringDataMenuCategoryRepository extends JpaRepository<MenuCategory, MenuCategoryId> {

    @Query("select count(c) > 0 from MenuCategory c where c.propertyId = :propertyId and lower(c.name) = lower(:name)")
    boolean existsByName(@Param("propertyId") UUID propertyId, @Param("name") String name);

    @Query("select c from MenuCategory c where c.propertyId = :propertyId order by c.displayOrder, c.name")
    List<MenuCategory> findAllOrdered(@Param("propertyId") UUID propertyId);

    @Query("select c from MenuCategory c where c.propertyId = :propertyId and c.active = true "
            + "order by c.displayOrder, c.name")
    List<MenuCategory> findActiveOrdered(@Param("propertyId") UUID propertyId);
}
