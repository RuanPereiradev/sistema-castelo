package br.com.castel.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.app.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves that V2 ({@code username}, {@code token_version}) applies over data written under V1
 * without losing anything, and that its {@code username} backfill from {@code email} works.
 *
 * <p>By the time this class runs, the shared Postgres container from {@link
 * AbstractIntegrationTest} may already be fully migrated (V1 + V2) on the {@code public} schema,
 * because another test class's {@code @SpringBootTest} context started first. Inserting a row and
 * calling {@code flyway.migrate()} on that shared state again would prove nothing, since V2 would
 * already be applied. To genuinely control the sequence "apply only V1 -> insert data -> apply V2
 * -> assert", this test runs its own {@link Flyway} instances against a dedicated schema, sharing
 * only the underlying {@link DataSource} (and therefore the same running container) with the rest
 * of the suite, never the {@code public} schema or the Spring-managed {@code Flyway} bean.
 */
@SpringBootTest
class AuthMigrationTest extends AbstractIntegrationTest {

    private static final String SCHEMA = "auth_migration_test";
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void dropDedicatedSchema() {
        new JdbcTemplate(dataSource).execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void shouldBackfillUsernameFromEmailWithoutLosingV1DataWhenV2IsApplied() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations(MIGRATION_LOCATION)
                .baselineOnMigrate(false)
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        UUID propertyId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into " + SCHEMA + ".property (id, legal_name) values (?, ?)",
                propertyId, "Property under V1");

        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into " + SCHEMA + ".app_user (id, property_id, full_name, email, password_hash) "
                        + "values (?, ?, ?, ?, ?)",
                userId, propertyId, "Joao Silva", "joao.silva@castel.com", "bcrypt-hash");

        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations(MIGRATION_LOCATION)
                .baselineOnMigrate(false)
                .load()
                .migrate();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select id, property_id, full_name, email, password_hash, username, token_version, is_active "
                        + "from " + SCHEMA + ".app_user where id = ?",
                userId);

        assertThat(UUID.fromString(row.get("id").toString())).isEqualTo(userId);
        assertThat(UUID.fromString(row.get("property_id").toString())).isEqualTo(propertyId);
        assertThat(row.get("full_name")).isEqualTo("Joao Silva");
        assertThat(row.get("email")).isEqualTo("joao.silva@castel.com");
        assertThat(row.get("password_hash")).isEqualTo("bcrypt-hash");
        assertThat(row.get("is_active")).isEqualTo(true);
        assertThat(row.get("username")).isEqualTo("joao.silva");
        assertThat(row.get("token_version")).isEqualTo(0);
    }
}
