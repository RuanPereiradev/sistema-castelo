package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabItem;
import br.com.castel.restaurant.domain.TablePaymentRequest;
import br.com.castel.restaurant.domain.TablePaymentRequestItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A payment from the phone as the floor reads it: the table by its label, the amount frozen at
 * creation and each item with its frozen value and share of the charge, named as the tab has it.
 * {@code status} is {@code EXPIRED} from the end of the window on; {@code refunded} is true on a
 * {@code CONFIRMED} payment the waiter undid.
 */
public record TablePaymentRequestResponse(
        String id,
        String status,
        boolean refunded,
        String diningTableId,
        String diningTableLabel,
        String tabId,
        String subtotal,
        String serviceChargeRate,
        String serviceCharge,
        String amount,
        String requestedAt,
        String expiresAt,
        String resolvedAt,
        String resolvedBy,
        String refundedAt,
        String refundReason,
        String customerName,
        boolean includeServiceCharge,
        List<Item> items) {

    /** Units of one item: {@code quantity} is how many this payment covers, of the line's {@code lineQuantity}. */
    public record Item(
            String tabItemId,
            String itemName,
            String variantName,
            int quantity,
            int lineQuantity,
            String lineTotal,
            String serviceCharge) {
    }

    static TablePaymentRequestResponse from(TablePaymentRequest request, String diningTableLabel, Tab tab,
            Instant now) {
        return new TablePaymentRequestResponse(
                request.id().value().toString(),
                request.statusAt(now).name(),
                request.isRefunded(),
                request.diningTableId().value().toString(),
                diningTableLabel,
                request.tabId().value().toString(),
                request.subtotal().asString(),
                TabBillResponse.percentPoints(request.serviceChargeRate()),
                request.serviceCharge().asString(),
                request.amount().asString(),
                request.requestedAt().toString(),
                request.expiresAt().toString(),
                request.resolvedAt().map(Instant::toString).orElse(null),
                request.resolvedBy().map(UUID::toString).orElse(null),
                request.refundedAt().map(Instant::toString).orElse(null),
                request.refundReason().orElse(null),
                request.customerName(),
                request.includeServiceCharge(),
                request.items().stream().map(item -> itemFrom(item, tab)).toList());
    }

    private static Item itemFrom(TablePaymentRequestItem item, Tab tab) {
        Optional<TabItem> onTab = tab.items().stream().filter(candidate -> candidate.id().equals(item.tabItemId()))
                .findFirst();
        return new Item(
                item.tabItemId().value().toString(),
                onTab.map(TabItem::itemName).orElse(null),
                onTab.flatMap(TabItem::variantName).orElse(null),
                item.quantity(),
                onTab.map(TabItem::quantity).orElse(0),
                item.lineTotal().asString(),
                item.serviceCharge().asString());
    }
}
