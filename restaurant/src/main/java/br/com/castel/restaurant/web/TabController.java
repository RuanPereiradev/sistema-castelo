package br.com.castel.restaurant.web;

import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.restaurant.application.AddTabItemCommand;
import br.com.castel.restaurant.application.DiningTableService;
import br.com.castel.restaurant.application.TabService;
import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.MenuItemVariantId;
import br.com.castel.restaurant.domain.ModifierId;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabItemId;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tabs of the restaurant (decision #3): {@code ADMIN} and {@code WAITER} on every route;
 * {@code KITCHEN} and {@code FRONT_DESK} are refused.
 *
 * <p>Cancelling is {@code POST .../cancel} with a reason, never {@code DELETE}: nothing is removed
 * (decision #13). Every {@code @PathVariable} names its variable explicitly, as in
 * {@link MenuAdministrationController}.
 */
@RestController
@RequestMapping("/api/restaurant/tabs")
@PreAuthorize("hasAnyRole('ADMIN', 'WAITER')")
public class TabController {

    /** What the request sends for a modifier without a quantity: none, which the domain refuses. */
    private static final int NO_QUANTITY = 0;

    private final TabService tabs;
    private final DiningTableService diningTables;

    public TabController(TabService tabs, DiningTableService diningTables) {
        this.tabs = tabs;
        this.diningTables = diningTables;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TabResponse openTab(@Valid @RequestBody OpenTabRequest request) {
        DiningTableId diningTableId = request.getDiningTableId() == null
                ? null
                : DiningTableId.of(request.getDiningTableId());
        return respond(tabs.open(request.getOrigin(), diningTableId, request.getCardNumber()));
    }

    /** Only {@code OPEN} and {@code CLOSING} tabs, by the moment they opened. */
    @GetMapping
    public List<TabSummaryResponse> listActiveTabs(
            @RequestParam(name = "diningTableId", required = false) String diningTableId,
            @RequestParam(name = "cardNumber", required = false) Integer cardNumber) {
        List<Tab> active = tabs.listActive(
                diningTableId == null ? null : DiningTableId.of(diningTableId), cardNumber);
        Map<DiningTableId, String> labels = diningTables.list(true).stream()
                .collect(Collectors.toMap(DiningTable::id, DiningTable::label, (first, second) -> first));
        return active.stream()
                .map(tab -> TabSummaryResponse.from(tab, tab.diningTableId().map(labels::get).orElse(null)))
                .toList();
    }

    @GetMapping("/{tabId}")
    public TabResponse getTab(@PathVariable("tabId") String tabId) {
        return respond(tabs.find(TabId.of(tabId)));
    }

    @PostMapping("/{tabId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public TabResponse addItem(@PathVariable("tabId") String tabId, @Valid @RequestBody AddTabItemRequest request) {
        return respond(tabs.addItem(TabId.of(tabId), commandFrom(request)));
    }

    @PostMapping("/{tabId}/items/{itemId}/cancel")
    public TabResponse cancelItem(
            @PathVariable("tabId") String tabId,
            @PathVariable("itemId") String itemId,
            @RequestBody CancellationRequest request) {
        return respond(tabs.cancelItem(TabId.of(tabId), TabItemId.of(itemId), request.getReason()));
    }

    @PostMapping("/{tabId}/cancel")
    public TabResponse cancelTab(@PathVariable("tabId") String tabId, @RequestBody CancellationRequest request) {
        return respond(tabs.cancel(TabId.of(tabId), request.getReason()));
    }

    private TabResponse respond(Tab tab) {
        String diningTableLabel = tab.diningTableId()
                .map(id -> diningTables.find(id).label())
                .orElse(null);
        return TabResponse.from(tab, diningTableLabel);
    }

    private static AddTabItemCommand commandFrom(AddTabItemRequest request) {
        List<AddTabItemCommand.ChosenModifier> modifiers = request.getModifiers() == null
                ? List.of()
                : request.getModifiers().stream()
                        .map(chosen -> new AddTabItemCommand.ChosenModifier(
                                ModifierId.of(chosen.getModifierId()),
                                chosen.getQuantity() == null ? NO_QUANTITY : chosen.getQuantity()))
                        .toList();
        return new AddTabItemCommand(
                MenuItemId.of(request.getMenuItemId()),
                request.getVariantId() == null ? null : MenuItemVariantId.of(request.getVariantId()),
                request.getQuantity(),
                request.getWeightGrams(),
                modifiers,
                request.getSpecialInstructions());
    }
}
