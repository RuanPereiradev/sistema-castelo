package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.DiningTableId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data's view of {@code dining_table}, used only by {@link JpaDiningTableRepository}. */
interface SpringDataDiningTableRepository extends JpaRepository<DiningTable, DiningTableId> {

    @Query("select count(t) > 0 from DiningTable t where t.propertyId = :propertyId and lower(t.label) = lower(:label)")
    boolean existsByLabel(@Param("propertyId") UUID propertyId, @Param("label") String label);

    @Query("select count(t) > 0 from DiningTable t where t.propertyId = :propertyId "
            + "and lower(t.label) = lower(:label) and t.id <> :excludedId")
    boolean existsByLabelExcluding(
            @Param("propertyId") UUID propertyId,
            @Param("label") String label,
            @Param("excludedId") DiningTableId excludedId);

    @Query("select t from DiningTable t where t.propertyId = :propertyId "
            + "and (:includeInactive = true or t.active = true)")
    List<DiningTable> findAll(@Param("propertyId") UUID propertyId, @Param("includeInactive") boolean includeInactive);
}
