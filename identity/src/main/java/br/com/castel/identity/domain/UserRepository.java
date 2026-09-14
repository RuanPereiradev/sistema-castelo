package br.com.castel.identity.domain;

import br.com.castel.identity.api.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link User}. Implemented in {@code identity.infra}.
 *
 * <p>Username lookups are case-insensitive: they find a row whose stored username differs from the
 * input only in letter case, which is how the {@code uk_app_user_username} index on
 * {@code lower(username)} defines uniqueness.
 */
public interface UserRepository {

    Optional<User> findById(UserId id);

    /**
     * The user whose username equals {@code username} once both are lowercased, without any lock.
     *
     * <p>The input is normalized in Java by {@link User#normalizedUsername(String)}, never by the
     * database, so the lookup and the login rate limit key agree on which inputs name the same user.
     * The database's own {@code lower()} follows the locale of the database and folds characters
     * that Java's {@code Locale.ROOT} keeps apart (for instance {@code İ} into {@code i}).
     */
    Optional<User> findByUsername(String username);

    /**
     * Same lookup as {@link #findById(UserId)}, holding a row-level write lock until the enclosing
     * transaction ends.
     *
     * <p>{@code tokenVersion} is bumped by a read-then-increment-then-save round trip, which is a
     * lost-update race under two concurrent requests for the same user: both would compute the
     * same {@code +1}, two tokens would share one {@code tokenVersion}, and both would stay valid.
     * Locking the row serializes those requests instead.
     */
    Optional<User> findByIdForUpdate(UserId id);

    /** Every user, ordered by username. */
    List<User> findAllOrderedByUsername();

    User save(User user);
}
