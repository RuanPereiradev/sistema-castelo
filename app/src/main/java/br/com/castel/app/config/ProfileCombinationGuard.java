package br.com.castel.app.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Aborts the boot when the {@code dev} and {@code prod} profiles are active together: {@code dev}
 * carries a default JWT secret, a seed with a known password and open CORS, and none of that may
 * ever reach production.
 *
 * <p>An {@link EnvironmentPostProcessor} (registered in {@code META-INF/spring.factories}), so it
 * runs once the profiles are known and before any bean, database connection or web server exists.
 */
public class ProfileCombinationGuard implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.matchesProfiles("dev") && environment.matchesProfiles("prod")) {
            throw new IllegalStateException(
                    "Profiles 'dev' and 'prod' must not be active together: 'dev' ships a default JWT secret, "
                            + "a seeded password and open CORS");
        }
    }

    /** After {@code ConfigDataEnvironmentPostProcessor}, which activates the profiles set in configuration files. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
