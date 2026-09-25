package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.EntityId;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/**
 * Identity of a {@code TabItem}.
 *
 * <p>Lives in {@code domain} and not in {@code api} (decision #16 of task 2.2): the {@code api}
 * package was frozen in task 0.6, and {@code billing} receives the tab as a plain {@code UUID}.
 */
@Embeddable
public record TabItemId(UUID value) implements EntityId {

    public TabItemId {
        EntityId.requireValid(value);
    }

    public static TabItemId newId() {
        return EntityId.newId(TabItemId::new);
    }

    public static TabItemId of(String value) {
        return EntityId.of(value, TabItemId::new);
    }
}
