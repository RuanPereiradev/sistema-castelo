package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.EntityId;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** Identity of a {@link TableOrderRequestLine}. */
@Embeddable
public record TableOrderRequestLineId(UUID value) implements EntityId {

    public TableOrderRequestLineId {
        EntityId.requireValid(value);
    }

    public static TableOrderRequestLineId newId() {
        return EntityId.newId(TableOrderRequestLineId::new);
    }

    public static TableOrderRequestLineId of(String value) {
        return EntityId.of(value, TableOrderRequestLineId::new);
    }
}
