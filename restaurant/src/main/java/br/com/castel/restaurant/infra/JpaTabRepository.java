package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabAlreadyOpenForCardException;
import br.com.castel.restaurant.domain.TabAlreadyOpenForDiningTableException;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.restaurant.domain.TabRepository;
import br.com.castel.restaurant.domain.TabsForTransfer;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
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

    /** How many times the tab of an item is looked for again when the item moves in between. */
    static final int ITEM_LOOKUP_ATTEMPTS = 3;

    private static final Comparator<TabId> TAB_LOCK_ORDER = Comparator.comparing(TabId::value);
    private static final Comparator<TabItemId> ITEM_LOCK_ORDER = Comparator.comparing(TabItemId::value);

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

    /** The tab first, then the item: the same order as ordering and closing take them. */
    @Override
    public Optional<Tab> findByIdForItemCancellation(TabId id, TabItemId itemId) {
        return springData.lockForKeyShare(id.value()).flatMap(locked -> {
            springData.lockItemForUpdate(id.value(), itemId.value());
            return springData.findById(id);
        });
    }

    @Override
    public Optional<Tab> findByIdForStatusChange(TabId id) {
        return springData.lockForUpdate(id.value()).flatMap(locked -> springData.findById(id));
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

    // ---- kitchen display

    @Override
    public Optional<Tab> findByIdForItemChange(TabId id, TabItemId itemId) {
        return findByIdForItemCancellation(id, itemId);
    }

    /**
     * Reads where the item is, locks that tab, then locks the item on it. The lock of the item
     * filters by tab, so it finds nothing when the item changed tab between the two statements —
     * which task 3.6 made possible. That is not an error: the item exists, somewhere else. The read
     * is repeated, and only a run of attempts all losing the race answers empty, which the caller
     * reports as the item not being found.
     *
     * <p>The order is always the tab and then the item, never the other way round, so this never
     * deadlocks against the closing or against a transfer.
     */
    @Override
    public Optional<Tab> findByItemIdForItemChange(TabItemId itemId) {
        for (int attempt = 0; attempt < ITEM_LOOKUP_ATTEMPTS; attempt++) {
            Optional<String> holder = springData.findTabIdOfItem(itemId.value());
            if (holder.isEmpty()) {
                return Optional.empty();
            }
            TabId tabId = TabId.of(holder.get());
            if (springData.lockForKeyShare(tabId.value()).isEmpty()) {
                return Optional.empty();
            }
            if (springData.lockItemForUpdate(tabId.value(), itemId.value()).isPresent()) {
                return springData.findById(tabId);
            }
        }
        return Optional.empty();
    }

    // ---- transfer and merge (task 3.6)

    @Override
    public Optional<TabsForTransfer> findForTransfer(TabId source, TabId destination, Set<TabItemId> itemIds) {
        if (!lockBoth(source, destination, id -> springData.lockForKeyShare(id.value()))) {
            return Optional.empty();
        }
        itemIds.stream()
                .sorted(ITEM_LOCK_ORDER)
                .forEach(itemId -> springData.lockItemForUpdate(source.value(), itemId.value()));
        return load(source, destination);
    }

    @Override
    public Optional<TabsForTransfer> findForMerge(TabId receiving, TabId merged) {
        boolean locked = lockBoth(receiving, merged, id -> id.equals(merged)
                ? springData.lockForUpdate(id.value())
                : springData.lockForKeyShare(id.value()));
        return locked ? load(merged, receiving) : Optional.empty();
    }

    /**
     * Takes the lock of each tab in one fixed order, so two opposite moves — merging A into B while
     * B is merged into A — queue instead of deadlocking. The order is the natural order of the two
     * ids and is the same for every operation of this task; what matters is that it is total and
     * shared, not which of the two ids it calls smaller.
     *
     * @param lock how each tab is locked, which differs between a transfer and a merge
     * @return whether both rows exist
     */
    private boolean lockBoth(TabId one, TabId other, Function<TabId, Optional<String>> lock) {
        return Stream.of(one, other)
                .sorted(TAB_LOCK_ORDER)
                .map(lock)
                .allMatch(Optional::isPresent);
    }

    /** Loads the two aggregates after their rows are locked, the way every other lock here does. */
    private Optional<TabsForTransfer> load(TabId source, TabId destination) {
        return springData.findById(source)
                .flatMap(loaded -> springData.findById(destination)
                        .map(other -> new TabsForTransfer(loaded, other)));
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
