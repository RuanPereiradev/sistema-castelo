package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.MenuItem;
import br.com.castel.restaurant.domain.MenuItemVariant;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A variant as anyone reading the public menu sees it. {@code availableNow} is false when the
 * variant ran out, and also when the item itself is not served at the moment of the request.
 */
public record PublicMenuVariantResponse(String id, String name, String price, boolean availableNow) {

    public static PublicMenuVariantResponse from(
            MenuItem item, MenuItemVariant variant, Instant moment, ZoneId propertyZone) {
        return new PublicMenuVariantResponse(
                variant.id().value().toString(),
                variant.name(),
                variant.unitPrice().asString(),
                item.isVariantAvailableAt(variant.id(), moment, propertyZone));
    }
}
