package br.com.castel.app.config;

import br.com.castel.sharedkernel.SettingRepository;
import br.com.castel.sharedkernel.Settings;
import br.com.castel.sharedkernel.SettingsFromRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Composes the typed reading of {@code setting}: a JDBC adapter, wrapped in a cache, behind the
 * {@link Settings} port every module reads configuration through.
 */
@Configuration
public class SettingConfig {

    @Bean
    public SettingRepository settingRepository(JdbcClient jdbcClient) {
        return new CachingSettingRepository(new JdbcSettingRepository(jdbcClient));
    }

    @Bean
    public Settings settings(SettingRepository settingRepository) {
        return new SettingsFromRepository(settingRepository);
    }
}
