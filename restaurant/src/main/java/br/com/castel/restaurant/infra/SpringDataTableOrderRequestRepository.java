package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.TableOrderRequest;
import br.com.castel.restaurant.domain.TableOrderRequestId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data's view of {@code table_order_request}, used only by {@link JpaTableOrderRequestRepository}. */
interface SpringDataTableOrderRequestRepository extends JpaRepository<TableOrderRequest, TableOrderRequestId> {

    /** Locks the row {@code FOR UPDATE} and answers its id; the aggregate is loaded afterwards. */
    @Query(value = "select cast(id as varchar) from table_order_request where id = :id for update",
            nativeQuery = true)
    Optional<String> lockForUpdate(@Param("id") UUID id);

    @Query(value = "select cast(id as varchar) from table_order_request "
            + "where dining_table_id = :diningTableId and status = 'PENDING' and expires_at <= :now "
            + "for update", nativeQuery = true)
    Optional<String> lockOverdueOnDiningTable(
            @Param("diningTableId") UUID diningTableId, @Param("now") Instant now);

    @Query(value = "select cast(id as varchar) from table_order_request "
            + "where property_id = :propertyId and status = 'PENDING' and expires_at <= :now "
            + "order by id for update", nativeQuery = true)
    List<String> lockOverdue(@Param("propertyId") UUID propertyId, @Param("now") Instant now);

    @Query("select r from TableOrderRequest r where r.id = :id and r.diningTableId = :diningTableId")
    Optional<TableOrderRequest> findByIdOnDiningTable(
            @Param("id") TableOrderRequestId id, @Param("diningTableId") DiningTableId diningTableId);

    @Query("select r from TableOrderRequest r where r.diningTableId = :diningTableId "
            + "and r.status = br.com.castel.restaurant.domain.TableOrderRequestStatus.PENDING "
            + "and r.expiresAt > :now")
    Optional<TableOrderRequest> findOpenOnDiningTable(
            @Param("diningTableId") DiningTableId diningTableId, @Param("now") Instant now);

    @Query("select r from TableOrderRequest r where r.propertyId = :propertyId "
            + "and r.status = br.com.castel.restaurant.domain.TableOrderRequestStatus.PENDING "
            + "and r.expiresAt > :now order by r.requestedAt, r.id")
    List<TableOrderRequest> findOpen(@Param("propertyId") UUID propertyId, @Param("now") Instant now);
}
