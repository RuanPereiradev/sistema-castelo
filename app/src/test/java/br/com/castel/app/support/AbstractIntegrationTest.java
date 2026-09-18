package br.com.castel.app.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The one PostgreSQL instance every integration test of this module talks to.
 *
 * <p>The database is shared, but the installation it holds is single-property (decision #27): the
 * context resolves the single {@code property} row while it starts and refuses to come up when it
 * finds more than one. Each test class runs under its own context configuration, so a class that
 * seeded its own property would bring down the next class's context. The property is therefore
 * seeded once here, before any context starts, and every test writes against {@link #PROPERTY_ID}.
 *
 * <p>Seeding needs the schema, so Flyway runs here too. The Flyway of each context then finds
 * nothing pending, which is what {@code BaselineMigrationTest} already asserts.
 */
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /** The single property of the test installation, seeded before the first context starts. */
    protected static final UUID PROPERTY_ID = UUID.fromString("0b7e3b8e-3c52-4c1e-9d0e-4a1f00000001");

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    static {
        POSTGRES.start();
        migrate();
        seedTheSingleProperty();
    }

    private static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static void seedTheSingleProperty() {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement statement = connection.prepareStatement(
                        "insert into property (id, legal_name) values (?, ?) on conflict (id) do nothing")) {
            statement.setObject(1, PROPERTY_ID);
            statement.setString(2, "Integration Property");
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not seed the single property of the test database", failure);
        }
    }
}
