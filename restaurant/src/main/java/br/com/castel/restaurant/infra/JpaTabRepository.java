package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabAlreadyOpenForCardException;
import br.com.castel.restaurant.domain.TabAlreadyOpenForDiningTableException;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Answers the {@link TabRepository} port over Spring Data JPA.
 *
 * <p>Opening a tab does not ask first whether the table or the card is free (decision #14): it
 * inserts and flushes, and the partial unique indexes answer. A violation of one of them becomes the
 * conflict of the domain, whether the other tab was opened an hour ago or a millisecond ago by a
 * concurrent request — one path for both, so the tested path is the one that runs in the race.
 */
@Repository
class JpaTabRepository implements TabRepository {

    static final String OPEN_BY_TABLE_INDEX = "idx_tab_open_by_table";
    static final String OPEN_BY_CARD_INDEX = "idx_tab_open_by_card";

    private final SpringDataTabRepository springData;

    JpaTabRepository(SpringDataTabRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<Tab> findById(TabId id) {
        return springData.findById(id);
    }

    /**
     * Locks first, then loads: the lock is a native statement, and loading the aggregate through
     * the entity manager keeps its collections fetched the way the mapping says.
     */
    @Override
    public Optional<Tab> findByIdForItemEntry(TabId id) {
        return springData.lockForKeyShare(id.value()).flatMap(locked -> springData.findById(id));
    }

    @Override
    public Tab add(Tab tab) {
        try {
            return springData.saveAndFlush(tab);
        } catch (DataIntegrityViolationException violation) {
            throw translate(violation, tab);
        }
    }

    @Override
    public Tab save(Tab tab) {
        return springData.save(tab);
    }

    @Override
    public List<Tab> findActive(UUID propertyId, DiningTableId diningTableIdOrNull, Integer cardNumberOrNull) {
        UUID diningTableId = diningTableIdOrNull == null ? null : diningTableIdOrNull.value();
        return springData.findActive(propertyId, diningTableId, cardNumberOrNull);
    }

    @Override
    public boolean existsActiveOnDiningTable(DiningTableId diningTableId) {
        return springData.existsActiveOnDiningTable(diningTableId);
    }

    /** The conflict of the domain for the index that refused the row; anything else goes on as it was. */
    private static RuntimeException translate(DataIntegrityViolationException violation, Tab tab) {
        String constraint = constraintNameOf(violation);
        if (OPEN_BY_TABLE_INDEX.equals(constraint)) {
            return new TabAlreadyOpenForDiningTableException("Dining table "
                    + tab.diningTableId().map(id -> id.value().toString()).orElse("") + " already has an open tab");
        }
        if (OPEN_BY_CARD_INDEX.equals(constraint)) {
            return new TabAlreadyOpenForCardException(
                    "Card " + tab.cardNumber().map(String::valueOf).orElse("") + " already has an open tab");
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
