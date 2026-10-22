package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.TablePaymentRequest;
import java.time.Instant;
import java.util.List;

/**
 * A payment as the phone that sent it reads it: the amount frozen at creation, the Pix code to pay it,
 * and its status, {@code EXPIRED} from the end of its window on. {@code refunded} is true on a
 * {@code CONFIRMED} payment the waiter undid. Nothing about who answered it.
 */
public record PublicTablePaymentRequestResponse(
        String id,
        String status,
        boolean refunded,
        String subtotal,
        String serviceCharge,
        String amount,
        boolean includeServiceCharge,
        String pixPayload,
        List<Item> items,
        String requestedAt,
        String expiresAt) {

    /** Units of one item the payment covers, with their value and share of the charge. */
    public record Item(String tabItemId, int quantity, String lineTotal, String serviceCharge) {
    }

    static PublicTablePaymentRequestResponse from(TablePaymentRequest request, Instant now) {
        return new PublicTablePaymentRequestResponse(
                request.id().value().toString(),
                request.statusAt(now).name(),
                request.isRefunded(),
                request.subtotal().asString(),
                request.serviceCharge().asString(),
                request.amount().asString(),
                request.includeServiceCharge(),
                request.pixPayload(),
                request.items().stream()
                        .map(item -> new Item(item.tabItemId().value().toString(), item.quantity(),
                                item.lineTotal().asString(), item.serviceCharge().asString()))
                        .toList(),
                request.requestedAt().toString(),
                request.expiresAt().toString());
    }
}
