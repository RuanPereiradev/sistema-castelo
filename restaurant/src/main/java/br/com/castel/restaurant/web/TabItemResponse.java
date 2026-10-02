package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.TabItem;
import br.com.castel.restaurant.domain.TabItemModifier;
import br.com.castel.sharedkernel.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One item of the tab as the waiter reads it, with the prices frozen at the moment of the order.
 * Money travels as a decimal string, the weight in whole grams, authors as UUIDs; what does not
 * apply to the item is null.
 *
 * <p>{@code transferredFromTabId} is the <em>last</em> tab the line came from, which is what the
 * screen shows; the whole trail, hop by hop, is in {@code tab_item_transfer}.
 */
public record TabItemResponse(
        String id,
        String menuItemId,
        String itemName,
        String variantId,
        String variantName,
        int quantity,
        Integer weightGrams,
        String unitPrice,
        String pricePerKilo,
        List<ModifierResponse> modifiers,
        String lineTotal,
        boolean serviceChargeable,
        String specialInstructions,
        String prepStation,
        String status,
        String orderedAt,
        String orderedBy,
        String preparationStartedAt,
        String readyAt,
        String deliveredAt,
        String cancelledAt,
        String cancelledBy,
        String cancellationReason,
        // ---- closing (task 3.2)
        boolean serviceChargeWaived,
        int splitGroup,
        // ---- transfer and merge (task 3.6)
        String transferredFromTabId,
        String transferredAt,
        String transferredBy) {

    public static TabItemResponse from(TabItem item) {
        return new TabItemResponse(
                item.id().value().toString(),
                item.menuItemId().value().toString(),
                item.itemName(),
                item.variantId().map(id -> id.value().toString()).orElse(null),
                item.variantName().orElse(null),
                item.quantity(),
                item.weight().map(weight -> weight.grams()).orElse(null),
                item.unitPrice().map(Money::asString).orElse(null),
                item.pricePerKilo().map(Money::asString).orElse(null),
                item.modifiers().stream().map(ModifierResponse::from).toList(),
                item.lineTotal().asString(),
                item.serviceChargeable(),
                item.specialInstructions().orElse(null),
                item.prepStation().name(),
                item.status().name(),
                item.orderedAt().toString(),
                item.orderedBy().toString(),
                item.preparationStartedAt().map(Instant::toString).orElse(null),
                item.readyAt().map(Instant::toString).orElse(null),
                item.deliveredAt().map(Instant::toString).orElse(null),
                item.cancelledAt().map(Instant::toString).orElse(null),
                item.cancelledBy().map(UUID::toString).orElse(null),
                item.cancellationReason().orElse(null),
                item.serviceChargeWaived(),
                item.splitGroup(),
                item.transferredFromTabId().map(id -> id.value().toString()).orElse(null),
                item.transferredAt().map(Instant::toString).orElse(null),
                item.transferredBy().map(UUID::toString).orElse(null));
    }

    /** A modifier as it went on the item: name and price frozen. */
    public record ModifierResponse(String modifierId, String name, String price, int quantity) {

        static ModifierResponse from(TabItemModifier modifier) {
            return new ModifierResponse(
                    modifier.modifierId().value().toString(),
                    modifier.modifierName(),
                    modifier.price().asString(),
                    modifier.quantity());
        }
    }
}
