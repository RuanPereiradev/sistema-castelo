package br.com.castel.billing.api;

import br.com.castel.sharedkernel.EntityId;
import java.util.UUID;

/** Identity of a {@code Folio}, the account a stay or a walk-in tab runs against. */
public record FolioId(UUID value) implements EntityId {

    public FolioId {
        EntityId.requireValid(value);
    }

    public static FolioId newId() {
        return EntityId.newId(FolioId::new);
    }

    public static FolioId of(String value) {
        return EntityId.of(value, FolioId::new);
    }
}
