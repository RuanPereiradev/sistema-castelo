package br.com.castel.restaurant.web;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.restaurant.application.KitchenDisplayService;
import br.com.castel.restaurant.domain.TabItemId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The kitchen display (K1): {@code KITCHEN} and {@code ADMIN} read the queue of a station and move
 * its items; the waiter delivers through {@link TabItemDeliveryController}.
 *
 * <p>One route per transition (K14), not the {@code PATCH .../status} of the plan: each has its
 * method on the aggregate and no {@code switch} lives here.
 */
@RestController
@RequestMapping("/api/kitchen")
@PreAuthorize("hasAnyRole('ADMIN', 'KITCHEN')")
public class KitchenDisplayController {

    /** The names of {@link PrepStation}: anything else, or nothing, is a 400 (decision #28). */
    private static final String STATION_NAMES = "KITCHEN|PIZZA|BAR";

    private final KitchenDisplayService kitchenDisplay;

    public KitchenDisplayController(KitchenDisplayService kitchenDisplay) {
        this.kitchenDisplay = kitchenDisplay;
    }

    @GetMapping("/queue")
    public KitchenQueueResponse getQueue(
            @RequestParam(name = "station", required = false) @NotNull @Pattern(regexp = STATION_NAMES)
                    String station) {
        return KitchenQueueResponse.from(kitchenDisplay.queue(PrepStation.valueOf(station)));
    }

    @PostMapping("/items/{itemId}/start")
    public KitchenTicketResponse startPreparation(@PathVariable("itemId") String itemId) {
        return KitchenTicketResponse.from(kitchenDisplay.startPreparation(TabItemId.of(itemId)));
    }

    @PostMapping("/items/{itemId}/ready")
    public KitchenTicketResponse markReady(@PathVariable("itemId") String itemId) {
        return KitchenTicketResponse.from(kitchenDisplay.markReady(TabItemId.of(itemId)));
    }

    @PostMapping("/items/{itemId}/undo")
    public KitchenTicketResponse undo(@PathVariable("itemId") String itemId) {
        return KitchenTicketResponse.from(kitchenDisplay.undo(TabItemId.of(itemId)));
    }
}
