package br.com.castel.restaurant.api;

import br.com.castel.sharedkernel.EntityId;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** Identity of a {@code MenuItem}. */
@Embeddable
public record MenuItemId(UUID value) implements EntityId {

    public MenuItemId {
        EntityId.requireValid(value);
    }

    public static MenuItemId newId() {
        return EntityId.newId(MenuItemId::new);
    }

    public static MenuItemId of(String value) {
        return EntityId.of(value, MenuItemId::new);
    }
}
