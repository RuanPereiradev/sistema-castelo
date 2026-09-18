package br.com.castel.identity.domain;

import br.com.castel.identity.api.Role;
import br.com.castel.identity.api.UserId;
import br.com.castel.sharedkernel.AuditedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * A person who can sign in to operate the property: front desk, waiter, kitchen or admin.
 *
 * <p>Login is by {@code username}, never by email, so a kitchen worker who has no email can
 * still authenticate. Concurrent sessions are not allowed: {@link #registerLogin(Clock)},
 * {@link #revokeSessions()}, {@link #changePassword(String, PasswordEncoder)} and
 * {@link #deactivate()} all bump {@code tokenVersion}, which invalidates every token issued
 * before the bump the moment it is checked through {@link #isSessionCurrent(int)}.
 */
@Entity
@Table(name = "app_user")
public class User extends AuditedEntity {

    /** Minimum length of a raw password, counted in Unicode code points. */
    public static final int MINIMUM_PASSWORD_LENGTH = 8;

    /** Maximum size of a raw password in UTF-8 bytes: BCrypt only reads the first 72 bytes and rejects more. */
    public static final int MAXIMUM_PASSWORD_BYTES = 72;

    /** Maximum length of a username, in characters. */
    public static final int MAXIMUM_USERNAME_LENGTH = 30;

    /** 3 to 30 lowercase letters, digits, dot or underscore; the input is normalized before matching. */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9._]{3," + MAXIMUM_USERNAME_LENGTH + "}");

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private UserId id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(nullable = false, length = 30)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column
    private String email;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "app_user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    protected User() {
        // required by JPA
    }

    private User(
            UserId id,
            UUID propertyId,
            String username,
            String passwordHash,
            String fullName,
            Set<Role> roles) {
        this.id = id;
        this.propertyId = propertyId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.roles = EnumSet.copyOf(roles);
        this.isActive = true;
        this.tokenVersion = 0;
        this.lastLoginAt = null;
    }

    /**
     * Creates a new active user with the given roles, hashing {@code rawPassword} with
     * {@code encoder}.
     *
     * @throws InvalidUsernameException if, once lowercased, {@code username} is not 3 to 30
     *         characters of lowercase letters, digits, dot or underscore
     * @throws WeakPasswordException if {@code rawPassword} has fewer than
     *         {@value #MINIMUM_PASSWORD_LENGTH} code points
     * @throws PasswordTooLongException if {@code rawPassword} is longer than
     *         {@value #MAXIMUM_PASSWORD_BYTES} bytes in UTF-8
     * @throws UserWithoutRolesException if {@code roles} is empty
     */
    public static User create(
            UUID propertyId,
            String username,
            String fullName,
            String rawPassword,
            Set<Role> roles,
            PasswordEncoder encoder) {
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(rawPassword, "rawPassword");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(encoder, "encoder");

        String normalizedUsername = requireWellFormedUsername(username);
        requireValidPassword(rawPassword);
        requireAtLeastOneRole(roles);
        return new User(
                UserId.newId(),
                propertyId,
                normalizedUsername,
                encoder.encode(rawPassword),
                fullName,
                roles);
    }

    /**
     * The username as every lookup and every key compares it: lowercased with {@link Locale#ROOT}.
     * A {@code null} input normalizes to the empty string, which is never well formed.
     *
     * <p>Normalizing does not validate: a Unicode input such as {@code "cozİnha"} lowercases to a
     * string that still holds a non-ASCII character, and {@link #isWellFormedUsername(String)}
     * rejects it.
     */
    public static String normalizedUsername(String rawUsername) {
        return rawUsername == null ? "" : rawUsername.toLowerCase(Locale.ROOT);
    }

    /**
     * Whether {@code rawUsername}, once normalized by {@link #normalizedUsername(String)}, follows
     * the username format: 3 to {@value #MAXIMUM_USERNAME_LENGTH} characters of lowercase ASCII
     * letters, digits, dot or underscore. The same rule {@link #create} enforces; no stored user can
     * have a username that fails it.
     */
    public static boolean isWellFormedUsername(String rawUsername) {
        return rawUsername != null && USERNAME_PATTERN.matcher(normalizedUsername(rawUsername)).matches();
    }

    private static String requireWellFormedUsername(String rawUsername) {
        if (rawUsername == null) {
            throw new InvalidUsernameException("Username must not be null");
        }
        if (!isWellFormedUsername(rawUsername)) {
            throw new InvalidUsernameException(
                    "Username must be 3 to 30 characters of lowercase letters, digits, dot or underscore");
        }
        return normalizedUsername(rawUsername);
    }

    private static void requireValidPassword(String rawPassword) {
        if (rawPassword.codePointCount(0, rawPassword.length()) < MINIMUM_PASSWORD_LENGTH) {
            throw new WeakPasswordException();
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_PASSWORD_BYTES) {
            throw new PasswordTooLongException();
        }
    }

    private static void requireAtLeastOneRole(Set<Role> roles) {
        if (roles.isEmpty()) {
            throw new UserWithoutRolesException();
        }
    }

    public void activate() {
        this.isActive = true;
    }

    /** Deactivates the user and invalidates every token issued so far. */
    public void deactivate() {
        this.isActive = false;
        this.tokenVersion++;
    }

    /**
     * Changes the password and invalidates every token issued so far.
     *
     * @throws WeakPasswordException if {@code rawPassword} has fewer than
     *         {@value #MINIMUM_PASSWORD_LENGTH} code points
     * @throws PasswordTooLongException if {@code rawPassword} is longer than
     *         {@value #MAXIMUM_PASSWORD_BYTES} bytes in UTF-8
     */
    public void changePassword(String rawPassword, PasswordEncoder encoder) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        Objects.requireNonNull(encoder, "encoder");
        requireValidPassword(rawPassword);
        this.passwordHash = encoder.encode(rawPassword);
        this.tokenVersion++;
    }

    /** Records a successful login at {@code clock}'s current instant and invalidates every token issued before it. */
    public void registerLogin(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        this.lastLoginAt = clock.instant();
        this.tokenVersion++;
    }

    /** Invalidates every token issued so far, without touching {@link #lastLoginAt()}. */
    public void revokeSessions() {
        this.tokenVersion++;
    }

    /**
     * Compares {@code rawPassword} against the stored hash, without ever exposing the hash itself.
     *
     * <p>Always runs the full hash comparison, whatever the user status, so the time spent does
     * not tell an active user from an inactive one.
     */
    public boolean matches(String rawPassword, PasswordEncoder encoder) {
        Objects.requireNonNull(encoder, "encoder");
        return encoder.matches(rawPassword, this.passwordHash);
    }

    /** Whether this user may sign in or keep using a session: only an active user can. */
    public boolean canAuthenticate() {
        return isActive;
    }

    /** Whether a token stamped with {@code tokenVersion} still belongs to this user's current session. */
    public boolean isSessionCurrent(int tokenVersion) {
        return this.tokenVersion == tokenVersion;
    }

    public UserId id() {
        return id;
    }

    public UUID propertyId() {
        return propertyId;
    }

    public String username() {
        return username;
    }

    public String fullName() {
        return fullName;
    }

    public String email() {
        return email;
    }

    public boolean isActive() {
        return isActive;
    }

    public int tokenVersion() {
        return tokenVersion;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public Set<Role> roles() {
        return Collections.unmodifiableSet(roles);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "User{id=%s, username=%s, roles=%s, isActive=%s}".formatted(id, username, roles, isActive);
    }
}
