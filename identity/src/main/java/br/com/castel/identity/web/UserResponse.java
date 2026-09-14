package br.com.castel.identity.web;

import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** One user in {@code GET /api/admin/users}. Never carries the password hash. */
public class UserResponse {

    private final UUID id;
    private final String username;
    private final String fullName;
    private final String email;
    private final boolean isActive;
    private final Set<Role> roles;
    private final Instant lastLoginAt;

    public UserResponse(
            UUID id, String username, String fullName, String email, boolean isActive, Set<Role> roles, Instant lastLoginAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.isActive = isActive;
        this.roles = roles;
        this.lastLoginAt = lastLoginAt;
    }

    public static UserResponse from(User user) {
        return new UserResponse(
                user.id().value(),
                user.username(),
                user.fullName(),
                user.email(),
                user.isActive(),
                user.roles(),
                user.lastLoginAt());
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

    /** Named {@code getIsActive} so the JSON field is {@code isActive}, not {@code active}. */
    public boolean getIsActive() {
        return isActive;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
