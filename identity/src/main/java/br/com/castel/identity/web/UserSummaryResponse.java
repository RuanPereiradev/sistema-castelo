package br.com.castel.identity.web;

import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import java.util.Set;
import java.util.UUID;

/** Minimal identification of the signed-in user, embedded in {@link LoginResponse}. */
public class UserSummaryResponse {

    private final UUID id;
    private final String username;
    private final String fullName;
    private final Set<Role> roles;

    public UserSummaryResponse(UUID id, String username, String fullName, Set<Role> roles) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.roles = roles;
    }

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.id().value(), user.username(), user.fullName(), user.roles());
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}
