package br.com.castel.restaurant.web;

import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.restaurant.application.DiningTableService;
import br.com.castel.restaurant.application.MenuItemService;
import br.com.castel.restaurant.application.ModifierService;
import br.com.castel.restaurant.application.TableOrderRequestService;
import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.MenuItem;
import br.com.castel.restaurant.domain.MenuItemVariant;
import br.com.castel.restaurant.domain.MenuItemVariantId;
import br.com.castel.restaurant.domain.Modifier;
import br.com.castel.restaurant.domain.ModifierId;
import br.com.castel.restaurant.domain.TableOrderRequest;
import br.com.castel.restaurant.domain.TableOrderRequestId;
import br.com.castel.restaurant.domain.TableOrderRequestLine;
import br.com.castel.restaurant.domain.TableOrderRequestLineModifier;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The floor's side of the requests sent from the QR code of the tables (task qr-table-ordering):
 * {@code ADMIN} and {@code WAITER}, any waiter on any request (decision #11).
 *
 * <p>Confirming and rejecting are {@code POST} without a body: the whole request, as it came
 * (decision #11). A confirmation refused by a rule of the ordering answers that rule's code, and the
 * request stays waiting (decision #21).
 */
@RestController
@RequestMapping("/api/restaurant/table-order-requests")
@PreAuthorize("hasAnyRole('ADMIN', 'WAITER')")
public class TableOrderRequestController {

    private final TableOrderRequestService requests;
    private final DiningTableService diningTables;
    private final MenuItemService menuItems;
    private final ModifierService modifiers;
    private final Clock clock;

    public TableOrderRequestController(
            TableOrderRequestService requests,
            DiningTableService diningTables,
            MenuItemService menuItems,
            ModifierService modifiers,
            Clock clock) {
        this.requests = requests;
        this.diningTables = diningTables;
        this.menuItems = menuItems;
        this.modifiers = modifiers;
        this.clock = clock;
    }

    /** The requests waiting for a waiter, oldest first. */
    @GetMapping
    public List<TableOrderRequestResponse> listPendingRequests() {
        List<TableOrderRequest> pending = requests.listPending();
        TableOrderRequestResponse.MenuNames names = currentNames();
        return pending.stream().map(request -> TableOrderRequestResponse.from(request, names, clock.instant())).toList();
    }

    @PostMapping("/{requestId}/confirm")
    public TableOrderRequestConfirmationResponse confirmRequest(@PathVariable("requestId") String requestId) {
        TableOrderRequest confirmed = requests.confirm(TableOrderRequestId.of(requestId));
        return new TableOrderRequestConfirmationResponse(
                respond(confirmed), confirmed.tabId().map(id -> id.value().toString()).orElse(null));
    }

    @PostMapping("/{requestId}/reject")
    public TableOrderRequestResponse rejectRequest(@PathVariable("requestId") String requestId) {
        return respond(requests.reject(TableOrderRequestId.of(requestId)));
    }

    private TableOrderRequestResponse respond(TableOrderRequest request) {
        return TableOrderRequestResponse.from(request, currentNames(), clock.instant());
    }

    /** The names as the menu has them now, read once per response. */
    private TableOrderRequestResponse.MenuNames currentNames() {
        Map<DiningTableId, String> tableLabels = diningTables.list(true).stream()
                .collect(Collectors.toMap(DiningTable::id, DiningTable::label, (first, second) -> first));
        Map<MenuItemId, MenuItem> itemsById = menuItems.listAll().stream()
                .collect(Collectors.toMap(MenuItem::id, Function.identity(), (first, second) -> first));
        Map<ModifierId, String> modifierNames = modifiers.listAll().stream()
                .collect(Collectors.toMap(Modifier::id, Modifier::name, (first, second) -> first));
        return new TableOrderRequestResponse.MenuNames() {
            @Override
            public String diningTableLabel(TableOrderRequest request) {
                return tableLabels.get(request.diningTableId());
            }

            @Override
            public String menuItemName(TableOrderRequestLine line) {
                MenuItem item = itemsById.get(line.menuItemId());
                return item == null ? null : item.name();
            }

            @Override
            public String variantName(TableOrderRequestLine line) {
                MenuItem item = itemsById.get(line.menuItemId());
                MenuItemVariantId variantId = line.variantId().orElse(null);
                return item == null || variantId == null
                        ? null
                        : item.variants().stream()
                                .filter(variant -> variant.id().equals(variantId))
                                .map(MenuItemVariant::name)
                                .findFirst()
                                .orElse(null);
            }

            @Override
            public String modifierName(TableOrderRequestLineModifier modifier) {
                return modifierNames.get(modifier.modifierId());
            }
        };
    }
}
