package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.EntityId;
import java.util.UUID;

/** Identity of a cash drawer session. Internal to billing: no other module names a session. */
public record CashDrawerSessionId(UUID value) implements EntityId {

    public CashDrawerSessionId {
        EntityId.requireValid(value);
    }

    public static CashDrawerSessionId newId() {
        return EntityId.newId(CashDrawerSessionId::new);
    }

    public static CashDrawerSessionId of(String value) {
        return EntityId.of(value, CashDrawerSessionId::new);
    }
}
