package br.com.castel.app.config;

import br.com.castel.sharedkernel.Setting;
import br.com.castel.sharedkernel.SettingRepository;
import br.com.castel.sharedkernel.SettingValueType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads and writes the {@code setting} table directly, with no JPA mapping.
 *
 * <p>Three columns, read by key, never joined: an entity would buy nothing here and would put an
 * {@code @Entity} in the composition root, which the architecture rules forbid.
 *
 * <p>Read by {@code setting_key} alone, which is unambiguous because {@link SinglePropertyId} has
 * already proved at boot that the database holds at most one property (decision #27). Writing does
 * need the id: a new key has to be inserted against some property.
 */
class JdbcSettingRepository implements SettingRepository {

    private static final String SELECT_BY_KEY =
            "select setting_key, setting_value, value_type from setting where setting_key = :settingKey";

    private static final String UPSERT_VALUE =
            "insert into setting (id, property_id, setting_key, setting_value, value_type) "
                    + "values (:id, :propertyId, :settingKey, :settingValue, :valueType) "
                    + "on conflict (property_id, setting_key) do update set setting_value = :settingValue";

    private final JdbcClient jdbcClient;
    private final SinglePropertyId singlePropertyId;

    JdbcSettingRepository(JdbcClient jdbcClient, SinglePropertyId singlePropertyId) {
        this.jdbcClient = jdbcClient;
        this.singlePropertyId = singlePropertyId;
    }

    @Override
    public Optional<Setting> findByKey(String settingKey) {
        return jdbcClient
                .sql(SELECT_BY_KEY)
                .param("settingKey", settingKey)
                .query((row, number) -> Setting.of(
                        row.getString("setting_key"),
                        row.getString("setting_value"),
                        SettingValueType.valueOf(row.getString("value_type"))))
                .optional();
    }

    /**
     * Inserts the key, or replaces the value of the key that is already there. The type of an
     * existing key is never rewritten: a setting that changes type is a different parameter.
     */
    @Override
    public void save(Setting setting) {
        jdbcClient
                .sql(UPSERT_VALUE)
                .param("id", UUID.randomUUID())
                .param("propertyId", singlePropertyId.value())
                .param("settingKey", setting.settingKey())
                .param("settingValue", setting.storedValue())
                .param("valueType", setting.valueType().name())
                .update();
    }
}
