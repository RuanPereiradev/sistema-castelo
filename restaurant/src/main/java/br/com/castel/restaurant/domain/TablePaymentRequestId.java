package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.EntityId;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** Identity of a {@link TablePaymentRequest}. */
@Embeddable
public record TablePaymentRequestId(UUID value) implements EntityId {

    public TablePaymentRequestId {
        EntityId.requireValid(value);
    }

    public static TablePaymentRequestId newId() {
        return EntityId.newId(TablePaymentRequestId::new);
    }

    public static TablePaymentRequestId of(String value) {
        return EntityId.of(value, TablePaymentRequestId::new);
    }
}
