package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.MenuItem;
import java.time.Instant;
import java.time.ZoneId;

/**
 * An item as anyone reading the public menu sees it.
 *
 * <p>Deliberately narrower than {@link MenuItemResponse}: no {@code prepStation}, no
 * {@code isActive}, no {@code displayOrder}, no audit columns. Which station prepares a dish is how
 * the kitchen is organised, not something a customer asked to know (decision #3).
 *
 * <p>{@code availableNow} already accounts for the schedule and for the kitchen having run out, read
 * at the moment of the request in the time zone of the property.
 */
public record PublicMenuItemResponse(
        String id, String name, String description, String price, boolean soldByWeight, boolean availableNow) {

    public static PublicMenuItemResponse from(MenuItem item, Instant moment, ZoneId propertyZone) {
        return new PublicMenuItemResponse(
                item.id().value().toString(),
                item.name(),
                item.description(),
                item.price().asString(),
                item.soldByWeight(),
                item.isAvailableAt(moment, propertyZone));
    }
}
