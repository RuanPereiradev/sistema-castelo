package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.EntityId;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** Identity of a {@link TabItemTransfer}. */
@Embeddable
public record TabItemTransferId(UUID value) implements EntityId {

    public TabItemTransferId {
        EntityId.requireValid(value);
    }

    public static TabItemTransferId newId() {
        return EntityId.newId(TabItemTransferId::new);
    }

    public static TabItemTransferId of(String value) {
        return EntityId.of(value, TabItemTransferId::new);
    }
}
