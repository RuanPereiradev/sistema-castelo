package br.com.castel.billing.infra;

import br.com.castel.billing.domain.CashDrawerSession;
import br.com.castel.billing.domain.CashDrawerSessionAlreadyOpenException;
import br.com.castel.billing.domain.CashDrawerSessionId;
import br.com.castel.billing.domain.CashDrawerSessionRepository;
import br.com.castel.billing.domain.CashDrawerSessionStatus;
import br.com.castel.billing.domain.CashPaymentTotals;
import br.com.castel.billing.domain.IdempotencyKeyReusedException;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Repository;

/**
 * Answers the {@link CashDrawerSessionRepository} port over JPA.
 *
 * <p>{@link #save} flushes on the spot, so a unique index broken by a concurrent transaction surfaces
 * here as the code of the rule it guards, instead of a 500 at commit.
 */
@Repository
class JpaCashDrawerSessionRepository implements CashDrawerSessionRepository {

    /** The unique constraints of the V8 that hold a rule of the domain, and the code each one answers. */
    private static final Map<String, Supplier<DomainException>> RULE_BY_CONSTRAINT = Map.of(
            "idx_cash_session_open",
            () -> new CashDrawerSessionAlreadyOpenException("The property already has an open cash drawer session"),
            "uk_cash_movement_idempotency",
            () -> new IdempotencyKeyReusedException("The idempotency key was used by another cash movement"));

    private static final String SUM_CONFIRMED_CASH_PAYMENTS =
            "select coalesce(sum(amount), 0), count(*) from payment "
                    + "where cash_drawer_session_id = :sessionId and status = 'CONFIRMED'";

    private final SpringDataCashDrawerSessionRepository springData;
    private final EntityManager entityManager;

    JpaCashDrawerSessionRepository(SpringDataCashDrawerSessionRepository springData, EntityManager entityManager) {
        this.springData = springData;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<CashDrawerSession> findById(CashDrawerSessionId id) {
        return springData.findById(id.value());
    }

    /**
     * Evicts the session from the persistence context before locking it, as the folio does (decision
     * #26 of task 1.3): otherwise a session already read in this transaction would be handed back as
     * it was before the lock, without a movement committed in between.
     */
    @Override
    public Optional<CashDrawerSession> findByIdForUpdate(CashDrawerSessionId id) {
        loadedInstanceOf(id).ifPresent(entityManager::detach);
        return springData.lockForUpdate(id.value()).flatMap(locked -> springData.findById(id.value()));
    }

    private Optional<Object> loadedInstanceOf(CashDrawerSessionId id) {
        SessionImplementor session = entityManager.unwrap(SessionImplementor.class);
        EntityPersister persister =
                session.getFactory().getMappingMetamodel().getEntityDescriptor(CashDrawerSession.class);
        return Optional.ofNullable(session.getPersistenceContextInternal()
                .getEntity(session.generateEntityKey(id.value(), persister)));
    }

    @Override
    public Optional<CashDrawerSession> findOpen(UUID propertyId) {
        return springData.findByStatus(propertyId, CashDrawerSessionStatus.OPEN);
    }

    @Override
    public Optional<CashDrawerSessionId> findOpenForKeyShare(UUID propertyId) {
        return springData.lockOpenForKeyShare(propertyId).map(CashDrawerSessionId::of);
    }

    @Override
    public void lockForKeyShare(CashDrawerSessionId id) {
        springData.lockForKeyShare(id.value());
    }

    @Override
    public Optional<CashDrawerSessionId> findSessionByMovementKey(String idempotencyKey) {
        return springData.findSessionIdByMovementKey(idempotencyKey).map(CashDrawerSessionId::of);
    }

    @Override
    public CashPaymentTotals sumConfirmedCashPayments(CashDrawerSessionId id) {
        Object[] row = (Object[]) entityManager.createNativeQuery(SUM_CONFIRMED_CASH_PAYMENTS)
                .setParameter("sessionId", id.value())
                .getSingleResult();
        return new CashPaymentTotals(Money.of((BigDecimal) row[0]), ((Number) row[1]).longValue());
    }

    /**
     * Persists a new session or flushes the changes of a loaded one. A session carries an assigned id,
     * so Spring Data's {@code save} would take it for a detached one and merge it with an extra select.
     */
    @Override
    public CashDrawerSession save(CashDrawerSession session) {
        try {
            if (!entityManager.contains(session)) {
                entityManager.persist(session);
            }
            entityManager.flush();
            return session;
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
