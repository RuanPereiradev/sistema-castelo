package br.com.castel.restaurant.web;

import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.restaurant.application.TableOrderRequestService;
import br.com.castel.restaurant.domain.MenuItemVariantId;
import br.com.castel.restaurant.domain.ModifierId;
import br.com.castel.restaurant.domain.TableOrderRequestId;
import br.com.castel.restaurant.domain.TableOrderRequestLine;
import br.com.castel.restaurant.domain.TableOrderRequestLineModifier;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer's side of the QR code of a table (task qr-table-ordering), open to anyone with the
 * code, under {@code /public} like the menu (decision #2 of task 1.2).
 *
 * <p>The code in the path is the only key: an unknown, regenerated or deactivated one answers
 * {@code DINING_TABLE_NOT_FOUND} without saying which (decision #24). Nothing here exposes a
 * preparation station, a user, a price or the bill of the table (decision #12); the menu itself is
 * read from {@code GET /public/menu}.
 */
@RestController
@RequestMapping("/public/tables/{orderingToken}")
public class PublicTableOrderingController {

    /** What the request sends for a modifier without a quantity: none, which the confirmation refuses. */
    private static final int NO_QUANTITY = 0;

    private final TableOrderRequestService requests;
    private final Clock clock;

    public PublicTableOrderingController(TableOrderRequestService requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    @GetMapping
    public PublicTableResponse readTable(@PathVariable("orderingToken") String orderingToken) {
        return PublicTableResponse.from(requests.viewTable(tokenOf(orderingToken)));
    }

    @PostMapping("/order-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public TableOrderRequestStatusResponse submitOrderRequest(
            @PathVariable("orderingToken") String orderingToken,
            @Valid @RequestBody SubmitTableOrderRequest request) {
        return TableOrderRequestStatusResponse.from(
                requests.submit(tokenOf(orderingToken), request.getCustomerName(), request.getCustomerDeviceId(),
                        linesFrom(request)),
                clock.instant());
    }

    @GetMapping("/order-requests/{requestId}")
    public TableOrderRequestStatusResponse readOrderRequest(
            @PathVariable("orderingToken") String orderingToken,
            @PathVariable("requestId") String requestId) {
        return TableOrderRequestStatusResponse.from(
                requests.findForTable(tokenOf(orderingToken), TableOrderRequestId.of(requestId)), clock.instant());
    }

    private static UUID tokenOf(String orderingToken) {
        return OrderingTokenPath.parse(orderingToken);
    }

    /** Null lines stay null, so the domain answers its own code for a request without lines. */
    private static List<TableOrderRequestLine> linesFrom(SubmitTableOrderRequest request) {
        return request.getLines() == null
                ? null
                : request.getLines().stream().map(PublicTableOrderingController::lineFrom).toList();
    }

    private static TableOrderRequestLine lineFrom(SubmitTableOrderRequest.Line line) {
        List<TableOrderRequestLineModifier> modifiers = line.getModifiers() == null
                ? List.of()
                : line.getModifiers().stream()
                        .map(chosen -> TableOrderRequestLineModifier.of(
                                ModifierId.of(chosen.getModifierId()),
                                chosen.getQuantity() == null ? NO_QUANTITY : chosen.getQuantity()))
                        .toList();
        return TableOrderRequestLine.of(
                MenuItemId.of(line.getMenuItemId()),
                line.getVariantId() == null ? null : MenuItemVariantId.of(line.getVariantId()),
                line.getQuantity(),
                modifiers,
                line.getSpecialInstructions());
    }
}
