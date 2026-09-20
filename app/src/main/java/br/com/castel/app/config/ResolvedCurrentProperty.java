package br.com.castel.app.config;

import br.com.castel.sharedkernel.CurrentProperty;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Answers the {@link CurrentProperty} port with the single property resolved while the context
 * started.
 *
 * <p>The id comes from {@link SinglePropertyId}, which keeps the rule that more than one property
 * brings the boot down. The zone is read from the row on first use and kept: it is a column the
 * administration changes once in the life of the installation, and reading it per request would
 * mean a query on every public menu page.
 */
class ResolvedCurrentProperty implements CurrentProperty {

    private static final String SELECT_TIME_ZONE = "select time_zone from property where id = :id";

    private final SinglePropertyId singlePropertyId;
    private final JdbcClient jdbcClient;

    private volatile ZoneId timeZone;

    ResolvedCurrentProperty(SinglePropertyId singlePropertyId, JdbcClient jdbcClient) {
        this.singlePropertyId = singlePropertyId;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public UUID id() {
        return singlePropertyId.value();
    }

    @Override
    public ZoneId timeZone() {
        ZoneId resolved = timeZone;
        if (resolved == null) {
            resolved = readTimeZone();
            timeZone = resolved;
        }
        return resolved;
    }

    private ZoneId readTimeZone() {
        return jdbcClient
                .sql(SELECT_TIME_ZONE)
                .param("id", id())
                .query((row, number) -> ZoneId.of(row.getString("time_zone")))
                .single();
    }
}
