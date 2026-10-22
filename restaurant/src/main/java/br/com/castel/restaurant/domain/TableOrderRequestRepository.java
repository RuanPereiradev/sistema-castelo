package br.com.castel.restaurant.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link TableOrderRequest}. Implemented in {@code restaurant.infra}. */
public interface TableOrderRequestRepository {

    Optional<TableOrderRequest> findById(TableOrderRequestId id);

    /**
     * The request, with its row locked {@code FOR UPDATE} until the transaction ends (decision #23):
     * two waiters answering the same request run one after the other, and the second finds it
     * resolved.
     */
    Optional<TableOrderRequest> findByIdForUpdate(TableOrderRequestId id);

    /** The request, only when it was sent from this table: the customer's read. */
    Optional<TableOrderRequest> findByIdOnDiningTable(TableOrderRequestId id, DiningTableId diningTableId);

    /** The {@code PENDING} request of the table whose window is still open at {@code now}. */
    Optional<TableOrderRequest> findOpenOnDiningTable(DiningTableId diningTableId, Instant now);

    /**
     * The {@code PENDING} request of the table whose window is over at {@code now}, locked
     * {@code FOR UPDATE}. Two phones on the same table queue on it, and the second finds nothing.
     */
    Optional<TableOrderRequest> findOverdueOnDiningTableForUpdate(DiningTableId diningTableId, Instant now);

    /** The {@code PENDING} requests of the property still inside their window, oldest first. */
    List<TableOrderRequest> findOpen(UUID propertyId, Instant now);

    /**
     * The {@code PENDING} requests of the property whose window is over at {@code now}, locked
     * {@code FOR UPDATE}, for the expiry job. A request being answered by a waiter is waited for, and
     * left out once it is answered.
     */
    List<TableOrderRequest> findOverdueForUpdate(UUID propertyId, Instant now);

    /**
     * Inserts a new request and flushes, so the partial unique index answers now (decision #22).
     *
     * @throws TableOrderRequestAlreadyPendingException if the table already has a {@code PENDING} request
     */
    TableOrderRequest add(TableOrderRequest request);

    /**
     * Writes the change of a request already stored, and flushes: the expiry of the overdue request
     * of a table has to reach the database before the insert of the new one, which Hibernate would
     * otherwise run first.
     */
    TableOrderRequest save(TableOrderRequest request);
}
