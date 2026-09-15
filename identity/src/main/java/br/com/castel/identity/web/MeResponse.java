package br.com.castel.identity.web;

import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import java.util.Set;
import java.util.UUID;

/** Response of {@code GET /api/auth/me}. */
public class MeResponse {

    private final UUID id;
    private final String username;
    private final String fullName;
    private final String email;
    private final Set<Role> roles;

    public MeResponse(UUID id, String username, String fullName, String email, Set<Role> roles) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.roles = roles;
    }

    public static MeResponse from(User user) {
        return new MeResponse(user.id().value(), user.username(), user.fullName(), user.email(), user.roles());
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

    public String getEmail() {
        return email;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}
