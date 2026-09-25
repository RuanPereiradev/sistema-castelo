package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.EntityId;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/**
 * Identity of a {@code DiningTable}.
 *
 * <p>Lives in {@code domain} and not in {@code api} (decision #9 of task 1.5): only the {@code Tab},
 * in this same module, refers to it, and the {@code api} package was frozen in task 0.6.
 */
@Embeddable
public record DiningTableId(UUID value) implements EntityId {

    public DiningTableId {
        EntityId.requireValid(value);
    }

    public static DiningTableId newId() {
        return EntityId.newId(DiningTableId::new);
    }

    public static DiningTableId of(String value) {
        return EntityId.of(value, DiningTableId::new);
    }
}
