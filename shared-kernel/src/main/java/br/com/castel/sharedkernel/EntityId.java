package br.com.castel.sharedkernel;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Base of typed identifiers, so that one aggregate's id is never assignable to another's.
 *
 * <p>Concrete ids are records whose only abstract requirement is {@link #value()}:
 *
 * <pre>{@code
 * public record TabId(UUID value) implements EntityId {
 *
 *     public static TabId newId() {
 *         return EntityId.newId(TabId::new);
 *     }
 *
 *     public static TabId of(String value) {
 *         return EntityId.of(value, TabId::new);
 *     }
 * }
 * }</pre>
 *
 * <p>The creation mechanism lives in static generic methods that receive the record constructor,
 * because a record without a body cannot inherit static factories.
 */
public interface EntityId {

    UUID value();

    /** Creates an id of the given type backed by a random UUID. */
    static <T extends EntityId> T newId(Function<UUID, T> constructor) {
        return of(UUID.randomUUID(), constructor);
    }

    /**
     * Wraps an existing UUID in an id of the given type.
     *
     * @throws IllegalArgumentException if {@code value} is null
     */
    static <T extends EntityId> T of(UUID value, Function<UUID, T> constructor) {
        Objects.requireNonNull(constructor, "constructor");
        if (value == null) {
            throw new IllegalArgumentException("Entity id value must not be null");
        }
        return constructor.apply(value);
    }

    /**
     * Parses a UUID in canonical form (8-4-4-4-12 hex digits) into an id of the given type.
     *
     * @throws IllegalArgumentException if {@code value} is null or not a canonical UUID
     */
    static <T extends EntityId> T of(String value, Function<UUID, T> constructor) {
        if (value == null) {
            throw new IllegalArgumentException("Entity id value must not be null");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Entity id is not a valid UUID: " + value, exception);
        }
        if (!uuid.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Entity id is not a canonical UUID: " + value);
        }
        return of(uuid, constructor);
    }
}
