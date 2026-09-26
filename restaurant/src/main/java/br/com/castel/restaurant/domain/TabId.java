package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.EntityId;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/**
 * Identity of a {@code Tab}.
 *
 * <p>Lives in {@code domain} and not in {@code api} (decision #16 of task 2.2): the {@code api}
 * package was frozen in task 0.6, and {@code billing} receives the tab as a plain {@code UUID}.
 */
@Embeddable
public record TabId(UUID value) implements EntityId {

    public TabId {
        EntityId.requireValid(value);
    }

    public static TabId newId() {
        return EntityId.newId(TabId::new);
    }

    public static TabId of(String value) {
        return EntityId.of(value, TabId::new);
    }
}
