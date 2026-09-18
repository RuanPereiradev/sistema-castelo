package br.com.castel.app.config;

import br.com.castel.sharedkernel.Setting;
import br.com.castel.sharedkernel.SettingNotFoundException;
import br.com.castel.sharedkernel.SettingRepository;
import br.com.castel.sharedkernel.SettingValueType;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads and writes the {@code setting} table directly, with no JPA mapping.
 *
 * <p>Three columns, read by key, never joined: an entity would buy nothing here and would put an
 * {@code @Entity} in the composition root, which the architecture rules forbid.
 *
 * <p>Filtered by {@code setting_key} alone. Version 1 runs a single property, so the unique
 * constraint {@code (property_id, setting_key)} yields at most one row; if a second property ever
 * gets its own row for the same key, this query fails loudly instead of picking one at random.
 */
class JdbcSettingRepository implements SettingRepository {

    private static final String SELECT_BY_KEY =
            "select setting_key, setting_value, value_type from setting where setting_key = :settingKey";

    private static final String UPDATE_VALUE =
            "update setting set setting_value = :settingValue where setting_key = :settingKey";

    private final JdbcClient jdbcClient;

    JdbcSettingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
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

    @Override
    public void save(Setting setting) {
        int updatedRows = jdbcClient
                .sql(UPDATE_VALUE)
                .param("settingValue", setting.storedValue())
                .param("settingKey", setting.settingKey())
                .update();
        if (updatedRows == 0) {
            throw new SettingNotFoundException(setting.settingKey());
        }
    }
}
