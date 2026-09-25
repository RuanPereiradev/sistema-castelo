package br.com.castel.billing.infra;

import br.com.castel.billing.api.FolioStatus;
import br.com.castel.billing.api.FolioType;
import br.com.castel.billing.domain.Folio;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/** Spring Data's view of {@code folio}, used only by {@link JpaFolioRepository}. */
interface SpringDataFolioRepository extends JpaRepository<Folio, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Folio f where f.id = :id")
    Optional<Folio> findByIdForUpdate(@Param("id") UUID id);

    /*
     * Flush mode COMMIT: the use case asks this after changing the reference of a folio, and an
     * automatic flush would write the new code first and meet the unique index instead of answering.
     */
    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    @Query("select f from Folio f where f.propertyId = :propertyId and f.referenceCode = :referenceCode "
            + "and f.status = :status and f.type = :type")
    Optional<Folio> findByReferenceCode(
            @Param("propertyId") UUID propertyId,
            @Param("referenceCode") String referenceCode,
            @Param("status") FolioStatus status,
            @Param("type") FolioType type);

    @Query("select count(f) > 0 from Folio f where f.type = :type and f.ownerId = :ownerId")
    boolean existsByOwner(@Param("type") FolioType type, @Param("ownerId") UUID ownerId);

    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    @Query("select f.id from Folio f join f.payments p where p.idempotencyKey = :idempotencyKey")
    Optional<UUID> findFolioIdByPaymentIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
