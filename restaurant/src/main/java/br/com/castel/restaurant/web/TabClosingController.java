package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.DiningTableService;
import br.com.castel.restaurant.application.TabClosingService;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.sharedkernel.Money;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Closing a tab (task 3.2), on the same base path as {@link TabController}: {@code ADMIN} and
 * {@code WAITER}, who is also the cashier of the restaurant and of the self-service (decision G4);
 * {@code KITCHEN} and {@code FRONT_DESK} are refused.
 *
 * <p>Three steps instead of the single {@code close} of the plan (decision F9): the pre-bill
 * ({@code closing}), the payments, the close. The routes that change the bill answer the pre-bill;
 * the two that change the status for good answer the tab.
 */
@RestController
@RequestMapping("/api/restaurant/tabs")
@PreAuthorize("hasAnyRole('ADMIN', 'WAITER')")
public class TabClosingController {

    /** Read as an optional header on purpose (decision #19 of task 1.3): billing refuses it absent. */
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final TabClosingService closing;
    private final DiningTableService diningTables;

    public TabClosingController(TabClosingService closing, DiningTableService diningTables) {
        this.closing = closing;
        this.diningTables = diningTables;
    }

    @PutMapping("/{tabId}/service-charge")
    public TabBillResponse changeServiceCharge(
            @PathVariable("tabId") String tabId, @Valid @RequestBody ServiceChargeRequest request) {
        TabId id = TabId.of(tabId);
        return TabBillResponse.from(request.getApplied()
                ? closing.restoreServiceCharge(id)
                : closing.removeServiceCharge(id));
    }

    @PutMapping("/{tabId}/items/{itemId}/service-charge")
    public TabBillResponse changeItemServiceCharge(
            @PathVariable("tabId") String tabId,
            @PathVariable("itemId") String itemId,
            @Valid @RequestBody ServiceChargeRequest request) {
        TabId id = TabId.of(tabId);
        TabItemId item = TabItemId.of(itemId);
        return TabBillResponse.from(request.getApplied()
                ? closing.restoreServiceChargeTo(id, item)
                : closing.removeServiceChargeFrom(id, item));
    }

    /** An absent count is zero, which the domain refuses with {@code INVALID_GUEST_COUNT}. */
    @PutMapping("/{tabId}/guest-count")
    public TabBillResponse recordGuestCount(
            @PathVariable("tabId") String tabId, @RequestBody GuestCountRequest request) {
        int guestCount = request.getGuestCount() == null ? 0 : request.getGuestCount();
        return TabBillResponse.from(closing.recordGuestCount(TabId.of(tabId), guestCount));
    }

    @PutMapping("/{tabId}/split-groups")
    public TabBillResponse assignSplitGroups(
            @PathVariable("tabId") String tabId, @Valid @RequestBody SplitGroupsRequest request) {
        Map<TabItemId, Integer> assignments = new LinkedHashMap<>();
        request.getAssignments().forEach(assignment ->
                assignments.put(TabItemId.of(assignment.getItemId()), assignment.getSplitGroup()));
        return TabBillResponse.from(closing.assignSplitGroups(TabId.of(tabId), assignments));
    }

    /**
     * The pre-bill, with an even split in {@code parts}, of the whole tab or of one {@code splitGroup}
     * (decision D11).
     */
    @GetMapping("/{tabId}/bill")
    public TabBillResponse getBill(
            @PathVariable("tabId") String tabId,
            @RequestParam(name = "parts", required = false) Integer parts,
            @RequestParam(name = "splitGroup", required = false) Integer splitGroup) {
        return TabBillResponse.from(closing.bill(TabId.of(tabId)), parts, splitGroup);
    }

    @PostMapping("/{tabId}/closing")
    public TabBillResponse startClosing(@PathVariable("tabId") String tabId) {
        return TabBillResponse.from(closing.startClosing(TabId.of(tabId)));
    }

    @PostMapping("/{tabId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public TabPaymentResponse receivePayment(
            @PathVariable("tabId") String tabId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody TabPaymentRequest request) {
        return TabPaymentResponse.from(closing.receivePayment(
                TabId.of(tabId), request.getMethod(), Money.of(request.getAmount()), idempotencyKey));
    }

    @PostMapping("/{tabId}/reopen")
    public TabResponse reopen(@PathVariable("tabId") String tabId, @RequestBody ReopeningRequest request) {
        return respond(closing.reopen(TabId.of(tabId), request.getReason()));
    }

    @PostMapping("/{tabId}/close")
    public TabResponse close(@PathVariable("tabId") String tabId) {
        return respond(closing.close(TabId.of(tabId)));
    }

    private TabResponse respond(Tab tab) {
        String diningTableLabel = tab.diningTableId()
                .map(id -> diningTables.find(id).label())
                .orElse(null);
        return TabResponse.from(tab, diningTableLabel, closing.currentServiceChargeRate());
    }
}
