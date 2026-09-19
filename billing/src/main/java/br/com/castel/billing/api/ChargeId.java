package br.com.castel.billing.api;

import br.com.castel.sharedkernel.EntityId;
import java.util.UUID;

/** Identity of a {@code Charge}, one posting on a folio. */
public record ChargeId(UUID value) implements EntityId {

    public ChargeId {
        EntityId.requireValid(value);
    }

    public static ChargeId newId() {
        return EntityId.newId(ChargeId::new);
    }

    public static ChargeId of(String value) {
        return EntityId.of(value, ChargeId::new);
    }
}
