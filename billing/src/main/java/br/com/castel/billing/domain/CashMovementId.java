package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.EntityId;
import java.util.UUID;

/** Identity of a cash drop or a cash supply of a {@link CashDrawerSession}. */
public record CashMovementId(UUID value) implements EntityId {

    public CashMovementId {
        EntityId.requireValid(value);
    }

    public static CashMovementId newId() {
        return EntityId.newId(CashMovementId::new);
    }

    public static CashMovementId of(String value) {
        return EntityId.of(value, CashMovementId::new);
    }
}
