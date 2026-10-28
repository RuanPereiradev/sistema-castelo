package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.TableOrderRequest;
import br.com.castel.restaurant.domain.TableOrderRequestAlreadyPendingException;
import br.com.castel.restaurant.domain.TableOrderRequestId;
import br.com.castel.restaurant.domain.TableOrderRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Answers the {@link TableOrderRequestRepository} port over Spring Data JPA.
 *
 * <p>Sending a request does not ask first whether the table has one pending (decision #22): it
 * inserts and flushes, and the partial unique index answers, the same way the opening of a tab does.
 * Every lock is a native statement taken before the aggregate is loaded through the entity manager,
 * as in {@link JpaTabRepository}: a {@code FOR UPDATE} over the outer join of an eager collection is
 * refused by PostgreSQL.
 */
@Repository
class JpaTableOrderRequestRepository implements TableOrderRequestRepository {

    static final String PENDING_BY_DEVICE_INDEX = "idx_table_order_request_pending_by_device";

    private final SpringDataTableOrderRequestRepository springData;

    JpaTableOrderRequestRepository(SpringDataTableOrderRequestRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<TableOrderRequest> findById(TableOrderRequestId id) {
        return springData.findById(id);
    }

    @Override
    public Optional<TableOrderRequest> findByIdForUpdate(TableOrderRequestId id) {
        return springData.lockForUpdate(id.value()).flatMap(locked -> springData.findById(id));
    }

    @Override
    public Optional<TableOrderRequest> findByIdOnDiningTable(TableOrderRequestId id, DiningTableId diningTableId) {
        return springData.findByIdOnDiningTable(id, diningTableId);
    }

    @Override
    public Optional<TableOrderRequest> findOpenOnDiningTableForDevice(
            DiningTableId diningTableId, String customerDeviceId, Instant now) {
        return springData.findOpenOnDiningTableForDevice(diningTableId, customerDeviceId, now);
    }

    @Override
    public int countOpenOnDiningTable(DiningTableId diningTableId, Instant now) {
        return Math.toIntExact(springData.countOpenOnDiningTable(diningTableId, now));
    }

    @Override
    public List<TableOrderRequest> findOverdueOnDiningTableForUpdate(DiningTableId diningTableId, Instant now) {
        List<TableOrderRequestId> locked = springData.lockOverdueOnDiningTable(diningTableId.value(), now).stream()
                .map(TableOrderRequestId::of)
                .toList();
        return springData.findAllById(locked);
    }

    @Override
    public List<TableOrderRequest> findOpen(UUID propertyId, Instant now) {
        return springData.findOpen(propertyId, now);
    }

    /** Locked in the order of the ids, so two runs of the job never wait on each other in a cycle. */
    @Override
    public List<TableOrderRequest> findOverdueForUpdate(UUID propertyId, Instant now) {
        List<TableOrderRequestId> locked = springData.lockOverdue(propertyId, now).stream()
                .map(TableOrderRequestId::of)
                .toList();
        return springData.findAllById(locked);
    }

    @Override
    public TableOrderRequest add(TableOrderRequest request) {
        try {
            return springData.saveAndFlush(request);
        } catch (DataIntegrityViolationException violation) {
            throw translate(violation);
        }
    }

    @Override
    public TableOrderRequest save(TableOrderRequest request) {
        return springData.saveAndFlush(request);
    }

    /**
     * The detail names no table: it reaches the phone, which is not authenticated (decision #38).
     */
    private static RuntimeException translate(DataIntegrityViolationException violation) {
        if (PENDING_BY_DEVICE_INDEX.equals(constraintNameOf(violation))) {
            return new TableOrderRequestAlreadyPendingException(
                    "This device already has a pending request on this dining table");
        }
        return violation;
    }

    private static String constraintNameOf(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return constraintViolation.getConstraintName();
            }
        }
        return null;
    }
}
