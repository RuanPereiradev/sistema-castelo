package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.Modifier;
import br.com.castel.restaurant.domain.ModifierId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data's view of {@code modifier}, used only by {@link JpaModifierRepository}. */
interface SpringDataModifierRepository extends JpaRepository<Modifier, ModifierId> {

    @Query("select count(m) > 0 from Modifier m where m.propertyId = :propertyId and lower(m.name) = lower(:name)")
    boolean existsByName(@Param("propertyId") UUID propertyId, @Param("name") String name);

    @Query("select count(m) > 0 from Modifier m where m.propertyId = :propertyId and lower(m.name) = lower(:name) "
            + "and m.id <> :excludedId")
    boolean existsByNameExcluding(
            @Param("propertyId") UUID propertyId, @Param("name") String name, @Param("excludedId") ModifierId excludedId);

    @Query("select m from Modifier m where m.propertyId = :propertyId order by m.name")
    List<Modifier> findAllOrdered(@Param("propertyId") UUID propertyId);
}
