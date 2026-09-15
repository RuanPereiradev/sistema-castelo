package br.com.castel.identity.api;

import br.com.castel.sharedkernel.EntityId;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/**
 * Identity of a user, backed by a time-ordered (version 7) UUID.
 *
 * <p>Public so other modules can reference a user (for instance, the operator behind a change)
 * without ever seeing the {@code User} aggregate itself.
 */
@Embeddable
public record UserId(UUID value) implements EntityId {

    public UserId {
        EntityId.requireValid(value);
    }

    public static UserId newId() {
        return EntityId.newId(UserId::new);
    }

    public static UserId of(String value) {
        return EntityId.of(value, UserId::new);
    }
}
