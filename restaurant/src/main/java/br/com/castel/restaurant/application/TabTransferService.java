package br.com.castel.restaurant.application;

import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.DiningTableNotFoundException;
import br.com.castel.restaurant.domain.DiningTableRepository;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabBilling;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.restaurant.domain.TabItemTransferRepository;
import br.com.castel.restaurant.domain.TabMove;
import br.com.castel.restaurant.domain.TabNotFoundException;
import br.com.castel.restaurant.domain.TabRepository;
import br.com.castel.restaurant.domain.TabTransferResult;
import br.com.castel.restaurant.domain.TabsForTransfer;
import br.com.castel.sharedkernel.AuditorAware;
import java.time.Clock;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moving items between tabs (task 3.6): the transfer of whole lines, the merge of two tabs and the
 * change of table.
 *
 * <p>Only orchestration: every rule lives in {@link Tab}. A class of its own, apart from
 * {@link TabService} and {@link TabClosingService}, so three tasks do not compete for one file.
 *
 * <p>Locks, taken by the repository in one fixed order of the two ids so that two opposite moves
 * queue instead of deadlocking: a transfer takes both tabs {@code FOR KEY SHARE}, since waiters go
 * on ordering on either of them, and then each item {@code FOR UPDATE}; a merge takes the absorbed
 * tab {@code FOR UPDATE}, waiting for every ordering in progress, and the one that stays
 * {@code FOR KEY SHARE}; a change of table takes the current tab {@code FOR UPDATE}.
 *
 * <p>Every move publishes one {@code TabItemTransferred} per item, which the kitchen display reads
 * to show the ticket under the table the item is on now, and writes one row per item on the trail,
 * in the same transaction.
 */
@Service
public class TabTransferService {

    private final TabRepository tabs;
    private final TabItemTransferRepository transfers;
    private final DiningTableRepository diningTables;
    private final TabBilling billing;
    private final AuditorAware auditorAware;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public TabTransferService(
            TabRepository tabs,
            TabItemTransferRepository transfers,
            DiningTableRepository diningTables,
            TabBilling billing,
            AuditorAware auditorAware,
            Clock clock,
            ApplicationEventPublisher events) {
        this.tabs = tabs;
        this.transfers = transfers;
        this.diningTables = diningTables;
        this.billing = billing;
        this.auditorAware = auditorAware;
        this.clock = clock;
        this.events = events;
    }

    /**
     * Moves whole lines from one tab to another, both of them staying open.
     *
     * @throws TabNotFoundException if either tab does not exist
     */
    @Transactional
    public TabTransferView transfer(TabId sourceId, TabId destinationId, Set<TabItemId> itemIds) {
        TabsForTransfer pair = tabs.findForTransfer(sourceId, destinationId, itemIds)
                .orElseThrow(() -> missingOneOf(sourceId, destinationId));
        Tab source = pair.source();
        Tab destination = pair.destination();
        TabTransferResult result =
                source.transferItemsTo(destination, itemIds, auditorAware.currentAuditorId(), clock.instant());
        record(result);
        tabs.save(source);
        tabs.save(destination);
        return new TabTransferView(source, destination, result.movedItems());
    }

    /**
     * Absorbs one tab into another: the tab of the path stays, the one named is left {@code MERGED}.
     *
     * @return the tab that stays
     * @throws TabNotFoundException if either tab does not exist
     */
    @Transactional
    public Tab merge(TabId receivingId, TabId mergedId) {
        TabsForTransfer pair = tabs.findForMerge(receivingId, mergedId)
                .orElseThrow(() -> missingOneOf(receivingId, mergedId));
        Tab merged = pair.source();
        Tab receiving = pair.destination();
        TabTransferResult result =
                receiving.mergeWith(merged, billing, auditorAware.currentAuditorId(), clock.instant());
        record(result);
        tabs.save(merged);
        return tabs.save(receiving);
    }

    /**
     * Moves the tab to another dining table: a new tab is opened there and this one is absorbed into
     * it, in one transaction. The tab that answers has a different id.
     *
     * @return the new tab, on the destination table
     * @throws TabNotFoundException if the tab does not exist
     * @throws DiningTableNotFoundException if the destination table does not exist
     */
    @Transactional
    public Tab moveToTable(TabId tabId, DiningTableId diningTableId) {
        Tab current = tabs.findByIdForStatusChange(tabId).orElseThrow(() -> notFound(tabId));
        DiningTable destinationTable = diningTables.findById(diningTableId)
                .orElseThrow(() -> new DiningTableNotFoundException("No dining table " + diningTableId.value()));
        TabMove move = current.moveToTable(destinationTable, billing, auditorAware.currentAuditorId(), clock.instant());
        /*
         * The new tab is inserted and flushed first: the partial unique index answers now that the
         * destination table is taken, and the rows that point at the new tab — the items and the
         * merged_into_tab_id of the old one — have something to point at.
         */
        Tab newTab = tabs.add(move.newTab());
        tabs.save(current);
        record(move.result());
        return newTab;
    }

    /** The trail first, then the events: nothing is published for a move that failed to be stored. */
    private void record(TabTransferResult result) {
        transfers.saveAll(result.transfers());
        result.events().forEach(events::publishEvent);
    }

    private TabNotFoundException missingOneOf(TabId one, TabId other) {
        TabId missing = tabs.findById(one).isEmpty() ? one : other;
        return notFound(missing);
    }

    private static TabNotFoundException notFound(TabId tabId) {
        return new TabNotFoundException("No tab " + tabId.value());
    }
}
