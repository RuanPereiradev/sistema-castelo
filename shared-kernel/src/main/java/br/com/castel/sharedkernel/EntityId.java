package br.com.castel.sharedkernel;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Base of typed identifiers, so that one aggregate's id is never assignable to another's.
 *
 * <p>Concrete ids are records whose only abstract requirement is {@link #value()}. The compact
 * constructor calls {@link #requireValid(UUID)}, so no id can exist with a null value:
 *
 * <pre>{@code
 * public record TabId(UUID value) implements EntityId {
 *
 *     public TabId {
 *         EntityId.requireValid(value);
 *     }
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

    /**
     * Guards the value of a concrete id; meant to be called from its compact constructor.
     *
     * @return the same value, when valid
     * @throws InvalidEntityIdException if {@code value} is null
     */
    static UUID requireValid(UUID value) {
        if (value == null) {
            throw new InvalidEntityIdException("Entity id value must not be null");
        }
        return value;
    }

    /**
     * Creates an id of the given type backed by a time-ordered (version 7, RFC 9562) UUID.
     *
     * <p>Ordering is guaranteed only between ids generated in distinct milliseconds.
     */
    static <T extends EntityId> T newId(Function<UUID, T> constructor) {
        return of(UuidVersion7.next(), constructor);
    }

    /**
     * Creates a random (version 4) UUID for a token exposed outside the system, such as in a QR code.
     *
     * <p>Deliberately not version 7: a time-ordered value reveals when it was created and makes
     * neighbouring tokens easier to guess.
     */
    static UUID newPublicToken() {
        return UUID.randomUUID();
    }

    /**
     * Wraps an existing UUID in an id of the given type.
     *
     * @throws InvalidEntityIdException if {@code value} is null
     */
    static <T extends EntityId> T of(UUID value, Function<UUID, T> constructor) {
        Objects.requireNonNull(constructor, "constructor");
        return constructor.apply(requireValid(value));
    }

    /**
     * Parses a UUID in canonical form (8-4-4-4-12 hex digits, either case) into an id of the given type.
     *
     * @throws InvalidEntityIdException if {@code value} is null or not a canonical UUID
     */
    static <T extends EntityId> T of(String value, Function<UUID, T> constructor) {
        if (value == null) {
            throw new InvalidEntityIdException("Entity id value must not be null");
        }
        if (!isCanonicalUuid(value)) {
            throw new InvalidEntityIdException("Entity id is not a canonical UUID");
        }
        return of(UUID.fromString(value), constructor);
    }

    private static boolean isCanonicalUuid(String text) {
        return text.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }
}
