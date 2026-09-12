package br.com.castel.app.architecture.violations;

import br.com.castel.sharedkernel.EntityId;
import java.util.UUID;

/**
 * Fixture for B3: a record implementing {@link EntityId} whose compact constructor never
 * calls {@link EntityId#requireValid(UUID)}.
 */
public record RecordWithoutRequireValidCall(UUID value) implements EntityId {

    public RecordWithoutRequireValidCall {
        // deliberately does not call EntityId.requireValid(value)
    }
}
