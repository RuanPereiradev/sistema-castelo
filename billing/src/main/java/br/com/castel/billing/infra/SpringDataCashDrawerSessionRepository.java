package br.com.castel.billing.infra;

import br.com.castel.billing.domain.CashDrawerSession;
import br.com.castel.billing.domain.CashDrawerSessionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data's view of {@code cash_drawer_session}, used only by {@link JpaCashDrawerSessionRepository}.
 *
 * <p>The locks are native: JPQL has no {@code FOR KEY SHARE}, and its {@code PESSIMISTIC_WRITE} comes
 * out as {@code FOR NO KEY UPDATE} on PostgreSQL, which does not conflict with {@code FOR KEY SHARE}
 * (invariant 25 of task 2.4).
 */
interface SpringDataCashDrawerSessionRepository extends JpaRepository<CashDrawerSession, UUID> {

    @Query(value = "select cast(id as varchar) from cash_drawer_session where id = :id for update",
            nativeQuery = true)
    Optional<String> lockForUpdate(@Param("id") UUID id);

    @Query(value = "select cast(id as varchar) from cash_drawer_session where id = :id for key share",
            nativeQuery = true)
    Optional<String> lockForKeyShare(@Param("id") UUID id);

    /*
     * In READ COMMITTED, a row locked by a closing is re-read once the closing commits, and the
     * condition on the status is checked again against it: the payment then finds no open session.
     */
    @Query(value = "select cast(id as varchar) from cash_drawer_session "
            + "where property_id = :propertyId and status = 'OPEN' for key share",
            nativeQuery = true)
    Optional<String> lockOpenForKeyShare(@Param("propertyId") UUID propertyId);

    @Query("select s from CashDrawerSession s where s.propertyId = :propertyId and s.status = :status")
    Optional<CashDrawerSession> findByStatus(
            @Param("propertyId") UUID propertyId, @Param("status") CashDrawerSessionStatus status);

    @Query(value = "select cast(cash_drawer_session_id as varchar) from cash_movement "
            + "where idempotency_key = :idempotencyKey",
            nativeQuery = true)
    Optional<String> findSessionIdByMovementKey(@Param("idempotencyKey") String idempotencyKey);
}
