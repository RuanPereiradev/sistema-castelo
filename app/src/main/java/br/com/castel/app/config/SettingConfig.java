package br.com.castel.app.config;

import br.com.castel.sharedkernel.CurrentProperty;
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

    /**
     * Resolved eagerly, while the context starts: an installation with more than one property brings
     * the boot down here, not at the first read of a setting months from now.
     */
    @Bean
    public SinglePropertyId singlePropertyId(JdbcClient jdbcClient) {
        return SinglePropertyId.resolvedFrom(jdbcClient);
    }

    /** The port every module writing a {@code property_id} reads the value from. */
    @Bean
    public CurrentProperty currentProperty(SinglePropertyId singlePropertyId, JdbcClient jdbcClient) {
        return new ResolvedCurrentProperty(singlePropertyId, jdbcClient);
    }

    @Bean
    public SettingRepository settingRepository(JdbcClient jdbcClient, SinglePropertyId singlePropertyId) {
        return new CachingSettingRepository(new JdbcSettingRepository(jdbcClient, singlePropertyId));
    }

    @Bean
    public Settings settings(SettingRepository settingRepository) {
        return new SettingsFromRepository(settingRepository);
    }
}
