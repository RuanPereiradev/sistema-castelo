package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.Modifier;

/** A modifier of the catalog as the administration screen reads it. */
public record ModifierResponse(String id, String name, String price, boolean isActive) {

    public static ModifierResponse from(Modifier modifier) {
        return new ModifierResponse(
                modifier.id().value().toString(), modifier.name(), modifier.price().asString(), modifier.isActive());
    }
}
