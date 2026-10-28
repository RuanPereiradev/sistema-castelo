package br.com.castel.restaurant.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link TablePaymentRequest}. Implemented in {@code restaurant.infra}. */
public interface TablePaymentRequestRepository {

    Optional<TablePaymentRequest> findById(TablePaymentRequestId id);

    /**
     * The request, with its row locked {@code FOR UPDATE} until the transaction ends: two waiters
     * answering the same request run one after the other, and the second finds it answered. Taken
     * before the lock of the tab, always (decision #27).
     */
    Optional<TablePaymentRequest> findByIdForUpdate(TablePaymentRequestId id);

    /** The request, only when it was sent from this table: the phone's read. */
    Optional<TablePaymentRequest> findByIdOnDiningTable(TablePaymentRequestId id, DiningTableId diningTableId);

    /**
     * The {@code CONFIRMED} requests of the tab, refunded ones included, by the moment they were
     * confirmed: where the floor refunds from (decision #59). Empty for a tab with none, or unknown.
     */
    List<TablePaymentRequest> findConfirmedOnTab(TabId tabId);

    /** The {@code PENDING} requests of the property still inside their window, oldest first. */
    List<TablePaymentRequest> findOpen(UUID propertyId, Instant now);

    /**
     * The ids of the {@code PENDING} requests of the property past their window, read without a lock:
     * the job expires each one in a transaction of its own (decision #50).
     */
    List<TablePaymentRequestId> findOverdueIds(UUID propertyId, Instant now);

    /**
     * The {@code PENDING} requests of the tab past their window, locked {@code FOR UPDATE}, so a
     * submission and a closing free their units before the lock of the tab is taken (decision #52).
     */
    List<TablePaymentRequest> findOverdueOnTabForUpdate(TabId tabId, Instant now);

    TablePaymentRequest add(TablePaymentRequest request);

    TablePaymentRequest save(TablePaymentRequest request);
}
