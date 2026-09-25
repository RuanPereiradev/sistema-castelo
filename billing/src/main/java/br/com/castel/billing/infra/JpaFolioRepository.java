package br.com.castel.billing.infra;

import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.FolioOwner;
import br.com.castel.billing.api.FolioStatus;
import br.com.castel.billing.api.FolioType;
import br.com.castel.billing.api.OwnerType;
import br.com.castel.billing.domain.ChargeAlreadyReversedException;
import br.com.castel.billing.domain.Folio;
import br.com.castel.billing.domain.FolioAlreadyOpenedForOwnerException;
import br.com.castel.billing.domain.FolioReferenceAlreadyInUseException;
import br.com.castel.billing.domain.FolioRepository;
import br.com.castel.billing.domain.IdempotencyKeyReusedException;
import br.com.castel.sharedkernel.DomainException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;

/**
 * Answers the {@link FolioRepository} port over JPA.
 *
 * <p>{@link #save} flushes on the spot, so a unique constraint broken by a concurrent transaction
 * surfaces here, where it becomes the code of the rule it guards (decision #16 of task 1.3), instead
 * of a 500 at commit.
 */
@Repository
class JpaFolioRepository implements FolioRepository {

    /** The unique constraints of the V6 that hold a rule of the domain, and the code each one answers. */
    private static final Map<String, Supplier<DomainException>> RULE_BY_CONSTRAINT = Map.of(
            "uk_folio_owner",
            () -> new FolioAlreadyOpenedForOwnerException("The owner already has a folio"),
            "idx_folio_open_ref",
            () -> new FolioReferenceAlreadyInUseException("Another open stay folio uses this reference code"),
            "uk_payment_idempotency",
            () -> new IdempotencyKeyReusedException("The idempotency key was used by another payment"),
            "uk_charge_reversal",
            () -> new ChargeAlreadyReversedException("The charge was already reversed"));

    private final SpringDataFolioRepository springData;
    private final EntityManager entityManager;

    JpaFolioRepository(SpringDataFolioRepository springData, EntityManager entityManager) {
        this.springData = springData;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Folio> findById(FolioId id) {
        return springData.findById(id.value());
    }

    @Override
    public Optional<Folio> findByIdForUpdate(FolioId id) {
        return springData.findByIdForUpdate(id.value());
    }

    @Override
    public Optional<Folio> findOpenStayByReferenceCode(UUID propertyId, String referenceCode) {
        return springData.findByReferenceCode(propertyId, referenceCode, FolioStatus.OPEN, FolioType.STAY);
    }

    @Override
    public boolean existsByOwner(FolioOwner owner) {
        FolioType type = owner.type() == OwnerType.RESERVATION ? FolioType.STAY : FolioType.TAB;
        return springData.existsByOwner(type, owner.value());
    }

    @Override
    public Optional<FolioId> findPaymentByIdempotencyKey(String idempotencyKey) {
        return springData.findFolioIdByPaymentIdempotencyKey(idempotencyKey).map(FolioId::new);
    }

    /**
     * Persists a new folio or flushes the changes of a loaded one. A folio carries an assigned id, so
     * Spring Data's {@code save} would take it for a detached one and merge it with an extra select.
     */
    @Override
    public Folio save(Folio folio) {
        try {
            if (!entityManager.contains(folio)) {
                entityManager.persist(folio);
            }
            entityManager.flush();
            return folio;
        } catch (PersistenceException failure) {
            throw ruleBrokenBy(failure);
        }
    }

    private static RuntimeException ruleBrokenBy(PersistenceException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                Supplier<DomainException> rule = RULE_BY_CONSTRAINT.get(violation.getConstraintName());
                if (rule != null) {
                    return rule.get();
                }
            }
        }
        return failure;
    }
}
