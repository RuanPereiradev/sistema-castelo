package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.EntityId;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** Identity of a {@link TableOrderRequest}. */
@Embeddable
public record TableOrderRequestId(UUID value) implements EntityId {

    public TableOrderRequestId {
        EntityId.requireValid(value);
    }

    public static TableOrderRequestId newId() {
        return EntityId.newId(TableOrderRequestId::new);
    }

    public static TableOrderRequestId of(String value) {
        return EntityId.of(value, TableOrderRequestId::new);
    }
}
