package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TablePaymentRequest;
import br.com.castel.restaurant.domain.TablePaymentRequestId;
import br.com.castel.restaurant.domain.TablePaymentRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Answers the {@link TablePaymentRequestRepository} port over Spring Data JPA. Every lock is a native
 * statement taken before the aggregate is loaded, as in {@link JpaTabRepository}.
 */
@Repository
class JpaTablePaymentRequestRepository implements TablePaymentRequestRepository {

    private final SpringDataTablePaymentRequestRepository springData;

    JpaTablePaymentRequestRepository(SpringDataTablePaymentRequestRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<TablePaymentRequest> findById(TablePaymentRequestId id) {
        return springData.findById(id);
    }

    @Override
    public Optional<TablePaymentRequest> findByIdForUpdate(TablePaymentRequestId id) {
        return springData.lockForUpdate(id.value()).flatMap(locked -> springData.findById(id));
    }

    @Override
    public Optional<TablePaymentRequest> findByIdOnDiningTable(TablePaymentRequestId id, DiningTableId diningTableId) {
        return springData.findByIdOnDiningTable(id, diningTableId);
    }

    @Override
    public List<TablePaymentRequest> findOpen(UUID propertyId, Instant now) {
        return springData.findOpen(propertyId, now);
    }

    @Override
    public List<TablePaymentRequestId> findOverdueIds(UUID propertyId, Instant now) {
        return springData.findOverdueIds(propertyId, now).stream().map(TablePaymentRequestId::of).toList();
    }

    /** Locked in the order of the ids; they all hold the same tab, which is locked after them. */
    @Override
    public List<TablePaymentRequest> findOverdueOnTabForUpdate(TabId tabId, Instant now) {
        List<TablePaymentRequestId> locked = springData.lockOverdueOnTab(tabId.value(), now).stream()
                .map(TablePaymentRequestId::of)
                .toList();
        return springData.findAllById(locked);
    }

    /** Inserts and flushes, so a violation answers inside the request that caused it. */
    @Override
    public TablePaymentRequest add(TablePaymentRequest request) {
        return springData.saveAndFlush(request);
    }

    @Override
    public TablePaymentRequest save(TablePaymentRequest request) {
        return springData.save(request);
    }
}
