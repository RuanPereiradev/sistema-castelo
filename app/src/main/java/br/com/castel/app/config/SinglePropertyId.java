package br.com.castel.app.config;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The id of the one {@code property} this installation runs, resolved while the context starts.
 *
 * <p>Version 1 is single-property (decision #27). Every table still carries {@code property_id},
 * because adding it later would be far more expensive, but nothing in the API takes a property as a
 * parameter. That only holds while the database really has one property, so the count is checked at
 * boot: more than one row and the context goes down with a message naming the problem, instead of
 * some query silently reading the wrong row months later.
 *
 * <p>A database with no {@code property} row yet is not a failure: a freshly migrated database is
 * exactly that, and the row is created by the seed. Whoever needs the id to write asks for it and
 * gets a clear failure then.
 */
public final class SinglePropertyId {

    private static final String COUNT_PROPERTIES = "select id from property order by id";

    private static final String MORE_THAN_ONE = "This installation is single-property, but the 'property' table "
            + "holds %d rows (%s). Either remove the extra rows or upgrade to a multi-property build; reading "
            + "settings and writing audit columns cannot guess which property is the right one.";

    private static final String NONE = "No row exists in the 'property' table, so there is no property to write "
            + "against. Run the seed, or insert the property of this installation, before writing settings.";

    private final UUID value;

    private SinglePropertyId(UUID value) {
        this.value = value;
    }

    /**
     * Reads the single property of the database.
     *
     * @throws IllegalStateException if more than one property exists
     */
    public static SinglePropertyId resolvedFrom(JdbcClient jdbcClient) {
        List<UUID> propertyIds = jdbcClient
                .sql(COUNT_PROPERTIES)
                .query((row, number) -> row.getObject("id", UUID.class))
                .list();
        if (propertyIds.size() > 1) {
            throw new IllegalStateException(MORE_THAN_ONE.formatted(propertyIds.size(), propertyIds));
        }
        return new SinglePropertyId(propertyIds.isEmpty() ? null : propertyIds.getFirst());
    }

    /** For a test, or for any composition that already knows the id. */
    public static SinglePropertyId of(UUID value) {
        return new SinglePropertyId(value);
    }

    public boolean isResolved() {
        return value != null;
    }

    /**
     * @throws IllegalStateException if the database holds no property yet
     */
    public UUID value() {
        if (value == null) {
            throw new IllegalStateException(NONE);
        }
        return value;
    }
}
