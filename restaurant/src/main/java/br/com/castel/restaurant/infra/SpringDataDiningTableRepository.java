package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.DiningTableId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data's view of {@code dining_table}, used only by {@link JpaDiningTableRepository}. */
interface SpringDataDiningTableRepository extends JpaRepository<DiningTable, DiningTableId> {

    /** Locks the row {@code FOR NO KEY UPDATE} and answers its id. Native: JPQL has no such lock mode. */
    @Query(value = "select cast(id as varchar) from dining_table where id = :id for no key update",
            nativeQuery = true)
    Optional<String> lockForNoKeyUpdate(@Param("id") UUID id);

    @Query("select t from DiningTable t where t.orderingToken = :orderingToken and t.active = true")
    Optional<DiningTable> findActiveByOrderingToken(@Param("orderingToken") UUID orderingToken);

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
