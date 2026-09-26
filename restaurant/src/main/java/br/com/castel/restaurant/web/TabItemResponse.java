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
        String cancelledAt,
        String cancelledBy,
        String cancellationReason) {

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
                item.cancelledAt().map(Instant::toString).orElse(null),
                item.cancelledBy().map(UUID::toString).orElse(null),
                item.cancellationReason().orElse(null));
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
