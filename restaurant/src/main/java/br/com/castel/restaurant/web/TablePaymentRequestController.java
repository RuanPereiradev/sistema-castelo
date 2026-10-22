package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.DiningTableService;
import br.com.castel.restaurant.application.TablePaymentRequestService;
import br.com.castel.restaurant.application.TabService;
import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.TablePaymentRequest;
import br.com.castel.restaurant.domain.TablePaymentRequestId;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The floor's side of the payments from the phone of the tables (task qr-table-payment):
 * {@code ADMIN} and {@code WAITER}. The waiter confirms after seeing the receipt, rejects when it does
 * not match, and refunds a confirmed one to free its items (decisions #8, #13 and #21).
 */
@RestController
@RequestMapping("/api/restaurant/table-payment-requests")
@PreAuthorize("hasAnyRole('ADMIN', 'WAITER')")
public class TablePaymentRequestController {

    private final TablePaymentRequestService payments;
    private final TabService tabs;
    private final DiningTableService diningTables;
    private final Clock clock;

    public TablePaymentRequestController(
            TablePaymentRequestService payments, TabService tabs, DiningTableService diningTables, Clock clock) {
        this.payments = payments;
        this.tabs = tabs;
        this.diningTables = diningTables;
        this.clock = clock;
    }

    /** The payments waiting for the waiter, oldest first. */
    @GetMapping
    public List<TablePaymentRequestResponse> listPendingRequests() {
        Map<DiningTableId, String> labels = diningTables.list(true).stream()
                .collect(Collectors.toMap(DiningTable::id, DiningTable::label, (first, second) -> first));
        return payments.listPending().stream().map(request -> respond(request, labels)).toList();
    }

    @PostMapping("/{requestId}/confirm")
    public TablePaymentRequestResponse confirmRequest(@PathVariable("requestId") String requestId) {
        return respond(payments.confirm(TablePaymentRequestId.of(requestId)));
    }

    @PostMapping("/{requestId}/reject")
    public TablePaymentRequestResponse rejectRequest(@PathVariable("requestId") String requestId) {
        return respond(payments.reject(TablePaymentRequestId.of(requestId)));
    }

    @PostMapping("/{requestId}/refund")
    public TablePaymentRequestResponse refundRequest(
            @PathVariable("requestId") String requestId, @RequestBody TablePaymentRefundRequest request) {
        return respond(payments.refund(TablePaymentRequestId.of(requestId), request.getReason()));
    }

    private TablePaymentRequestResponse respond(TablePaymentRequest request) {
        return TablePaymentRequestResponse.from(request, diningTables.find(request.diningTableId()).label(),
                tabs.find(request.tabId()), clock.instant());
    }

    private TablePaymentRequestResponse respond(TablePaymentRequest request, Map<DiningTableId, String> labels) {
        return TablePaymentRequestResponse.from(request, labels.get(request.diningTableId()),
                tabs.find(request.tabId()), clock.instant());
    }
}
