package br.com.castel.restaurant.api;

import br.com.castel.sharedkernel.EntityId;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** Identity of a {@code MenuCategory}. */
@Embeddable
public record MenuCategoryId(UUID value) implements EntityId {

    public MenuCategoryId {
        EntityId.requireValid(value);
    }

    public static MenuCategoryId newId() {
        return EntityId.newId(MenuCategoryId::new);
    }

    public static MenuCategoryId of(String value) {
        return EntityId.of(value, MenuCategoryId::new);
    }
}
