package br.com.castel.identity.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only view of the user authenticated on the current request.
 *
 * <p>Carries only what authorization needs. It never holds a password hash, and it is not the
 * {@code User} aggregate, so no module outside {@code identity} can change a user through it.
 */
public record AuthenticatedUser(UserId id, String username, Set<Role> roles) {

    public AuthenticatedUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(roles, "roles");
        roles = roles.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(Role.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }
}
