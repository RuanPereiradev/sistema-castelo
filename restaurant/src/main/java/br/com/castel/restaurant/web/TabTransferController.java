package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.DiningTableService;
import br.com.castel.restaurant.application.TabClosingService;
import br.com.castel.restaurant.application.TabTransferService;
import br.com.castel.restaurant.application.TabTransferView;
import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.sharedkernel.Percentage;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Moving items between tabs (task 3.6), on the same base path as {@link TabController}.
 *
 * <p>{@code WAITER}, {@code ADMIN} and also {@code FRONT_DESK} (decision T12); {@code KITCHEN} is
 * refused. This is the first tab route open to the reception, against the pattern of decision #3 of
 * task 2.2, and it is an annotated exception: the reception has to be able to move a group from one
 * table to another without a waiter. Making every tab route uniform is a task of its own.
 *
 * <p>A class of its own, so this task does not compete with {@link TabController} or
 * {@link TabClosingController} for one file.
 */
@RestController
@RequestMapping("/api/restaurant/tabs")
@PreAuthorize("hasAnyRole('ADMIN', 'WAITER', 'FRONT_DESK')")
public class TabTransferController {

    private final TabTransferService transfers;
    private final DiningTableService diningTables;
    private final TabClosingService closing;

    public TabTransferController(
            TabTransferService transfers, DiningTableService diningTables, TabClosingService closing) {
        this.transfers = transfers;
        this.diningTables = diningTables;
        this.closing = closing;
    }

    /**
     * Moves whole lines to another tab. An absent list of items answers {@code INVALID_TAB_TRANSFER},
     * the same code an empty one gets: for the front there is no difference between asking to move
     * nothing and not saying what to move.
     */
    @PostMapping("/{tabId}/transfer")
    public TabTransferResponse transfer(
            @PathVariable("tabId") String tabId, @Valid @RequestBody TabTransferRequest request) {
        Percentage currentRate = closing.currentServiceChargeRate();
        TabTransferView view = transfers.transfer(
                TabId.of(tabId), TabId.of(request.getToTabId()), itemIdsOf(request.getItemIds()));
        return TabTransferResponse.from(
                view, labelOf(view.source()), labelOf(view.destination()), currentRate);
    }

    /** The tab of the path stays; the one named in the body is absorbed and left {@code MERGED}. */
    @PostMapping("/{tabId}/merge")
    public TabResponse merge(@PathVariable("tabId") String tabId, @Valid @RequestBody TabMergeRequest request) {
        Percentage currentRate = closing.currentServiceChargeRate();
        return respond(transfers.merge(TabId.of(tabId), TabId.of(request.getMergedTabId())), currentRate);
    }

    /**
     * Moves the tab to another table. Answers 201 with the <b>new</b> tab, which has a different id:
     * the old one is left {@code MERGED} pointing at it, and that is what gives the move its trail.
     */
    @PostMapping("/{tabId}/move")
    @ResponseStatus(HttpStatus.CREATED)
    public TabResponse moveToTable(@PathVariable("tabId") String tabId, @Valid @RequestBody TabMoveRequest request) {
        Percentage currentRate = closing.currentServiceChargeRate();
        return respond(
                transfers.moveToTable(TabId.of(tabId), DiningTableId.of(request.getDiningTableId())), currentRate);
    }

    /**
     * Repeated ids collapse. The order here does not decide anything: the aggregate moves the lines
     * in the order they sit on the tab, so the trail of one call reads the same way every time
     * whatever order the request listed.
     */
    private static Set<TabItemId> itemIdsOf(List<String> itemIds) {
        if (itemIds == null) {
            return Set.of();
        }
        Set<TabItemId> ids = new LinkedHashSet<>();
        itemIds.forEach(itemId -> ids.add(TabItemId.of(itemId)));
        return ids;
    }

    /** The rate comes read before the write, as in {@link TabController}. */
    private TabResponse respond(Tab tab, Percentage currentRate) {
        return TabResponse.from(tab, labelOf(tab), currentRate);
    }

    private String labelOf(Tab tab) {
        return tab.diningTableId().map(id -> diningTables.find(id).label()).orElse(null);
    }
}
