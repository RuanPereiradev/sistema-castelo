package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.DiningTableService;
import br.com.castel.restaurant.application.KitchenDisplayService;
import br.com.castel.restaurant.application.TabClosingService;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.sharedkernel.Percentage;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The waiter marking an item delivered (K1, K4), ready or not. Under the prefix of the tabs, in a
 * controller of its own so the routes of the tab are left to their task.
 */
@RestController
@RequestMapping("/api/restaurant/tabs")
@PreAuthorize("hasAnyRole('ADMIN', 'WAITER')")
public class TabItemDeliveryController {

    private final KitchenDisplayService kitchenDisplay;
    private final DiningTableService diningTables;
    private final TabClosingService closing;

    public TabItemDeliveryController(
            KitchenDisplayService kitchenDisplay, DiningTableService diningTables, TabClosingService closing) {
        this.kitchenDisplay = kitchenDisplay;
        this.diningTables = diningTables;
        this.closing = closing;
    }

    @PostMapping("/{tabId}/items/{itemId}/deliver")
    public TabResponse deliverItem(@PathVariable("tabId") String tabId, @PathVariable("itemId") String itemId) {
        Percentage currentRate = closing.currentServiceChargeRate();
        Tab tab = kitchenDisplay.deliver(TabId.of(tabId), TabItemId.of(itemId));
        String diningTableLabel = tab.diningTableId()
                .map(id -> diningTables.find(id).label())
                .orElse(null);
        return TabResponse.from(tab, diningTableLabel, currentRate);
    }
}
