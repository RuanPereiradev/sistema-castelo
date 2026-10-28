package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.TablePaymentRequestService;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.restaurant.domain.TablePaymentRequestId;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The phone of the table paying its items by Pix (task qr-table-payment), open to anyone with the QR
 * code of the table, under {@code /public} like the ordering.
 *
 * <p>The code in the path is the only key: an unknown, regenerated or deactivated one answers
 * {@code DINING_TABLE_NOT_FOUND}. A table with no active tab answers {@code TAB_NOT_FOUND}.
 */
@RestController
@RequestMapping("/public/tables/{orderingToken}")
public class PublicTablePaymentController {

    /** The header the phone sends its device code in when reading the tab (decision #46). */
    static final String CUSTOMER_DEVICE_HEADER = "X-Customer-Device";

    private final TablePaymentRequestService payments;
    private final Clock clock;

    public PublicTablePaymentController(TablePaymentRequestService payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    /**
     * The device reading comes in {@value #CUSTOMER_DEVICE_HEADER}, never in the URL, which goes to the
     * logs (decision #46); without it no item is marked as its own.
     */
    @GetMapping("/tab")
    public PublicTableTabResponse readTab(
            @PathVariable("orderingToken") String orderingToken,
            @RequestHeader(name = CUSTOMER_DEVICE_HEADER, required = false) String customerDeviceId) {
        return PublicTableTabResponse.from(
                payments.viewTab(OrderingTokenPath.parse(orderingToken), Optional.ofNullable(customerDeviceId)));
    }

    @PostMapping("/payment-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicTablePaymentRequestResponse submitPaymentRequest(
            @PathVariable("orderingToken") String orderingToken,
            @Valid @RequestBody SubmitTablePaymentRequest request) {
        return PublicTablePaymentRequestResponse.from(
                payments.submit(OrderingTokenPath.parse(orderingToken), request.getCustomerName(),
                        request.getCustomerDeviceId(), choicesOf(request.getItems()),
                        request.getIncludeServiceCharge()),
                clock.instant());
    }

    /** Simulates the amount while the customer chooses: reserves nothing, writes nothing (decision #55). */
    @PostMapping("/payment-requests/quote")
    public TablePaymentQuoteResponse quotePaymentRequest(
            @PathVariable("orderingToken") String orderingToken,
            @Valid @RequestBody QuoteTablePaymentRequest request) {
        return TablePaymentQuoteResponse.from(payments.quote(OrderingTokenPath.parse(orderingToken),
                choicesOf(request.getItems()), request.getIncludeServiceCharge()));
    }

    @GetMapping("/payment-requests/{requestId}")
    public PublicTablePaymentRequestResponse readPaymentRequest(
            @PathVariable("orderingToken") String orderingToken, @PathVariable("requestId") String requestId) {
        return PublicTablePaymentRequestResponse.from(
                payments.findForTable(OrderingTokenPath.parse(orderingToken), TablePaymentRequestId.of(requestId)),
                clock.instant());
    }

    /** A missing list is an empty one, which the domain refuses with its own code. */
    private static List<Tab.PartChoice> choicesOf(List<SubmitTablePaymentRequest.Item> items) {
        return items == null
                ? List.of()
                : items.stream()
                        .map(item -> new Tab.PartChoice(TabItemId.of(item.getTabItemId()), item.getQuantity()))
                        .toList();
    }
}
