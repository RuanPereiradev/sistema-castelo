package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.EntityId;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/**
 * Identity of a {@code Modifier}.
 *
 * <p>Lives in {@code domain} and not in {@code api}: no other module refers to it, and the
 * {@code api} package was frozen in task 0.6.
 */
@Embeddable
public record ModifierId(UUID value) implements EntityId {

    public ModifierId {
        EntityId.requireValid(value);
    }

    public static ModifierId newId() {
        return EntityId.newId(ModifierId::new);
    }

    public static ModifierId of(String value) {
        return EntityId.of(value, ModifierId::new);
    }
}
