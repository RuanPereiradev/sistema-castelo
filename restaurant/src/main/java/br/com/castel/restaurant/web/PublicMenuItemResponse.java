package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.MenuItem;
import br.com.castel.restaurant.domain.MenuItemVariant;
import br.com.castel.restaurant.domain.Modifier;
import br.com.castel.restaurant.domain.ModifierId;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * An item as anyone reading the public menu sees it.
 *
 * <p>Deliberately narrower than {@link MenuItemResponse}: no {@code prepStation}, no
 * {@code isActive}, no {@code displayOrder}, no audit columns. Which station prepares a dish is how
 * the kitchen is organised, not something a customer asked to know (decision #3).
 *
 * <p>{@code availableNow} already accounts for the schedule, for the kitchen having run out and, for
 * an item with variants, for every variant having run out — read at the moment of the request in
 * the time zone of the property.
 *
 * <p>{@code price} is the "from" price: the cheapest active variant, or the price of the item when
 * it has none (task 1.2, decision #12). {@code variants} carries only the active ones, and
 * {@code modifiers} only the ones active in the catalog.
 */
public record PublicMenuItemResponse(
        String id,
        String name,
        String description,
        String price,
        boolean soldByWeight,
        boolean availableNow,
        List<PublicMenuVariantResponse> variants,
        List<PublicMenuModifierResponse> modifiers) {

    /**
     * @param activeModifiersById the active modifiers of the catalog; an offered modifier missing here
     *     is inactive and is left out
     */
    public static PublicMenuItemResponse from(
            MenuItem item, Map<ModifierId, Modifier> activeModifiersById, Instant moment, ZoneId propertyZone) {
        return new PublicMenuItemResponse(
                item.id().value().toString(),
                item.name(),
                item.description(),
                item.startingPrice().asString(),
                item.soldByWeight(),
                item.isAvailableAt(moment, propertyZone),
                item.variants().stream()
                        .filter(MenuItemVariant::isActive)
                        .map(variant -> PublicMenuVariantResponse.from(item, variant, moment, propertyZone))
                        .toList(),
                item.modifiers().stream()
                        .filter(link -> activeModifiersById.containsKey(link.modifierId()))
                        .map(link -> PublicMenuModifierResponse.from(activeModifiersById.get(link.modifierId()), link))
                        .sorted(Comparator.comparing(PublicMenuModifierResponse::name))
                        .toList());
    }
}
