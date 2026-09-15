package br.com.castel.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.app.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the structure and behaviour of the V1 baseline migration
 * (tables property, setting, app_user, user_role) against a real
 * PostgreSQL instance, spun up by {@link AbstractIntegrationTest}.
 *
 * <p>Each test that inserts data runs inside a transaction that is rolled
 * back automatically at the end of the test, so tests don't leak state
 * into each other.
 */
@SpringBootTest
@Transactional
class BaselineMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    // ---------------------------------------------------------------
    // 1. Tables and columns exist
    // ---------------------------------------------------------------

    @Test
    void shouldCreateAllFourBaselineTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'public' and table_type = 'BASE TABLE'",
                String.class);

        assertThat(tables).contains("property", "setting", "app_user", "user_role");
    }

    @Test
    void propertyTableShouldHaveRequiredNotNullColumns() {
        assertColumnExistsAndNotNull("property", "id", true);
        assertColumnExistsAndNotNull("property", "legal_name", true);
        assertColumnExistsAndNotNull("property", "time_zone", true);
        assertColumnExistsAndNotNull("property", "trade_name", false);
        assertColumnExistsAndNotNull("property", "cnpj", false);
    }

    @Test
    void settingTableShouldHaveRequiredNotNullColumns() {
        assertColumnExistsAndNotNull("setting", "id", true);
        assertColumnExistsAndNotNull("setting", "property_id", true);
        assertColumnExistsAndNotNull("setting", "setting_key", true);
        assertColumnExistsAndNotNull("setting", "setting_value", true);
        assertColumnExistsAndNotNull("setting", "value_type", true);
        assertColumnExistsAndNotNull("setting", "description", false);
    }

    @Test
    void appUserTableShouldHaveRequiredNotNullColumns() {
        assertColumnExistsAndNotNull("app_user", "id", true);
        assertColumnExistsAndNotNull("app_user", "property_id", true);
        assertColumnExistsAndNotNull("app_user", "full_name", true);
        assertColumnExistsAndNotNull("app_user", "email", false);
        assertColumnExistsAndNotNull("app_user", "password_hash", true);
        assertColumnExistsAndNotNull("app_user", "is_active", true);
        assertColumnExistsAndNotNull("app_user", "last_login_at", false);
    }

    @Test
    void userRoleTableShouldHaveRequiredNotNullColumns() {
        assertColumnExistsAndNotNull("user_role", "app_user_id", true);
        assertColumnExistsAndNotNull("user_role", "role", true);
    }

    private void assertColumnExistsAndNotNull(String table, String column, boolean expectedNotNull) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select is_nullable from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = ?",
                table, column);

        assertThat(rows)
                .as("column %s.%s should exist", table, column)
                .hasSize(1);

        String isNullable = (String) rows.get(0).get("is_nullable");
        boolean actualNotNull = "NO".equals(isNullable);
        assertThat(actualNotNull)
                .as("column %s.%s not-null flag", table, column)
                .isEqualTo(expectedNotNull);
    }

    // ---------------------------------------------------------------
    // 2. setting: unique (property_id, setting_key)
    // ---------------------------------------------------------------

    @Test
    void shouldRejectDuplicateSettingKeyForSameProperty() {
        UUID propertyId = insertProperty();
        insertSetting(propertyId, "checkin.time");

        assertThatThrownBy(() -> insertSetting(propertyId, "checkin.time"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowSameSettingKeyForDifferentProperties() {
        UUID firstProperty = insertProperty();
        UUID secondProperty = insertProperty();

        assertThatCode(() -> {
            insertSetting(firstProperty, "checkin.time");
            insertSetting(secondProperty, "checkin.time");
        }).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------
    // 3. app_user: unique email
    // ---------------------------------------------------------------

    @Test
    void shouldRejectDuplicateEmailForAppUser() {
        UUID propertyId = insertProperty();
        insertAppUser(propertyId, "duplicate@castel.com");

        assertThatThrownBy(() -> insertAppUser(propertyId, "duplicate@castel.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowMultipleAppUsersWithNullEmail() {
        UUID propertyId = insertProperty();

        assertThatCode(() -> {
            insertAppUser(propertyId, null);
            insertAppUser(propertyId, null);
        }).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------
    // 4. user_role: CHECK on role
    // ---------------------------------------------------------------

    @Test
    void shouldRejectRoleOutsideAllowedEnumValues() {
        UUID propertyId = insertProperty();
        UUID userId = insertAppUser(propertyId, "checked@castel.com");

        assertThatThrownBy(() -> insertUserRole(userId, "SUPER_ADMIN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------
    // 5. user_role: composite primary key
    // ---------------------------------------------------------------

    @Test
    void shouldRejectDuplicateRoleForSameUser() {
        UUID propertyId = insertProperty();
        UUID userId = insertAppUser(propertyId, "waiter@castel.com");
        insertUserRole(userId, "WAITER");

        assertThatThrownBy(() -> insertUserRole(userId, "WAITER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowDifferentRolesForSameUser() {
        UUID propertyId = insertProperty();
        UUID userId = insertAppUser(propertyId, "multirole@castel.com");
        insertUserRole(userId, "WAITER");

        assertThatCode(() -> insertUserRole(userId, "KITCHEN")).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------
    // 6. user_role: ON DELETE CASCADE
    // ---------------------------------------------------------------

    @Test
    void shouldCascadeDeleteUserRolesWhenAppUserIsDeleted() {
        UUID propertyId = insertProperty();
        UUID userId = insertAppUser(propertyId, "cascade@castel.com");
        insertUserRole(userId, "ADMIN");
        insertUserRole(userId, "FRONT_DESK");

        jdbcTemplate.update("delete from app_user where id = ?", userId);

        Integer remaining = jdbcTemplate.queryForObject(
                "select count(*) from user_role where app_user_id = ?", Integer.class, userId);

        assertThat(remaining).isZero();
    }

    // ---------------------------------------------------------------
    // 7. extensions
    // ---------------------------------------------------------------

    @Test
    void shouldCreatePgcryptoAndBtreeGistExtensions() {
        List<String> extensions = jdbcTemplate.queryForList(
                "select extname from pg_extension where extname in ('pgcrypto', 'btree_gist')",
                String.class);

        assertThat(extensions).containsExactlyInAnyOrder("pgcrypto", "btree_gist");
    }

    // ---------------------------------------------------------------
    // 8. migration is idempotent / repeatable
    // ---------------------------------------------------------------

    @Test
    void shouldNotApplyAnyMigrationWhenMigratingASecondTime() {
        // Flyway already ran V1 during the Spring context startup for this test class,
        // so this second call is expected to find nothing pending to apply.
        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isZero();
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private UUID insertProperty() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into property (id, legal_name) values (?, ?)",
                id, "Property " + id);
        return id;
    }

    private void insertSetting(UUID propertyId, String settingKey) {
        jdbcTemplate.update(
                "insert into setting (id, property_id, setting_key, setting_value, value_type) "
                        + "values (?, ?, ?, ?, 'STRING')",
                UUID.randomUUID(), propertyId, settingKey, "some-value");
    }

    private UUID insertAppUser(UUID propertyId, String email) {
        UUID id = UUID.randomUUID();
        String username = email == null
                ? "user_" + id.toString().replace("-", "").substring(0, 8)
                : email.split("@")[0];
        jdbcTemplate.update(
                "insert into app_user (id, property_id, full_name, email, username, password_hash) "
                        + "values (?, ?, ?, ?, ?, ?)",
                id, propertyId, "User " + id, email, username, "hash");
        return id;
    }

    private void insertUserRole(UUID userId, String role) {
        jdbcTemplate.update(
                "insert into user_role (app_user_id, role) values (?, ?)", userId, role);
    }
}
