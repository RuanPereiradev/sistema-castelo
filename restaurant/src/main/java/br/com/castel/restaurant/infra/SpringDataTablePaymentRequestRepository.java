package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.TablePaymentRequest;
import br.com.castel.restaurant.domain.TablePaymentRequestId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data's view of {@code table_payment_request}, used only by {@link JpaTablePaymentRequestRepository}. */
interface SpringDataTablePaymentRequestRepository extends JpaRepository<TablePaymentRequest, TablePaymentRequestId> {

    /** Locks the row {@code FOR UPDATE} and answers its id; the aggregate is loaded afterwards. */
    @Query(value = "select cast(id as varchar) from table_payment_request where id = :id for update",
            nativeQuery = true)
    Optional<String> lockForUpdate(@Param("id") UUID id);

    @Query(value = "select cast(id as varchar) from table_payment_request "
            + "where property_id = :propertyId and status = 'PENDING' and expires_at <= :now "
            + "order by id", nativeQuery = true)
    List<String> findOverdueIds(@Param("propertyId") UUID propertyId, @Param("now") Instant now);

    @Query(value = "select cast(id as varchar) from table_payment_request "
            + "where tab_id = :tabId and status = 'PENDING' and expires_at <= :now "
            + "order by id for update", nativeQuery = true)
    List<String> lockOverdueOnTab(@Param("tabId") UUID tabId, @Param("now") Instant now);

    @Query("select r from TablePaymentRequest r where r.id = :id and r.diningTableId = :diningTableId")
    Optional<TablePaymentRequest> findByIdOnDiningTable(
            @Param("id") TablePaymentRequestId id, @Param("diningTableId") DiningTableId diningTableId);

    @Query("select r from TablePaymentRequest r where r.propertyId = :propertyId "
            + "and r.status = br.com.castel.restaurant.domain.TablePaymentRequestStatus.PENDING "
            + "and r.expiresAt > :now order by r.requestedAt, r.id")
    List<TablePaymentRequest> findOpen(@Param("propertyId") UUID propertyId, @Param("now") Instant now);
}
