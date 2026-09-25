package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.EntityId;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/**
 * Identity of a {@code MenuItemVariant}.
 *
 * <p>Lives in {@code domain} and not in {@code api}: no other module refers to it, and the
 * {@code api} package was frozen in task 0.6.
 */
@Embeddable
public record MenuItemVariantId(UUID value) implements EntityId {

    public MenuItemVariantId {
        EntityId.requireValid(value);
    }

    public static MenuItemVariantId newId() {
        return EntityId.newId(MenuItemVariantId::new);
    }

    public static MenuItemVariantId of(String value) {
        return EntityId.of(value, MenuItemVariantId::new);
    }
}
