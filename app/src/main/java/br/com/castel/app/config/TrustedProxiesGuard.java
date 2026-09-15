package br.com.castel.app.config;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * In {@code prod}, aborts the boot unless {@code server.tomcat.remoteip.internal-proxies} (from
 * {@code TRUSTED_PROXIES}) holds a usable value.
 *
 * <p>Left to Tomcat, an unresolved {@code ${TRUSTED_PROXIES}} fails later with a bare
 * {@link PatternSyntaxException}, and an empty value silently trusts no proxy, so behind the
 * reverse proxy every login would share the proxy's IP in the rate limit. A value with a
 * {@code /} is read by Tomcat as a CIDR list; anything else must compile as a regular expression.
 *
 * <p>An {@link EnvironmentPostProcessor} (registered in {@code META-INF/spring.factories}), so it
 * runs before the web server is created.
 */
public class TrustedProxiesGuard implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY = "server.tomcat.remoteip.internal-proxies";

    private static final String UNUSABLE = PROPERTY + " (TRUSTED_PROXIES) must be set in the 'prod' profile "
            + "to the reverse proxy address, as a CIDR list or a regular expression, but ";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.matchesProfiles("prod")) {
            return;
        }
        String trustedProxies = resolve(environment);
        if (trustedProxies == null || trustedProxies.isBlank()) {
            throw new IllegalStateException(UNUSABLE + "it is empty");
        }
        if (trustedProxies.contains("${")) {
            throw new IllegalStateException(UNUSABLE + "it holds an unresolved placeholder");
        }
        if (trustedProxies.indexOf('/') < 0) {
            requireValidRegularExpression(trustedProxies);
        }
    }

    private static String resolve(ConfigurableEnvironment environment) {
        try {
            return environment.getProperty(PROPERTY);
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            throw new IllegalStateException(UNUSABLE + "it holds an unresolved placeholder");
        }
    }

    private static void requireValidRegularExpression(String trustedProxies) {
        try {
            Pattern.compile(trustedProxies);
        } catch (PatternSyntaxException invalid) {
            throw new IllegalStateException(UNUSABLE + "it is neither a CIDR list nor a valid regular expression");
        }
    }

    /** After {@code ConfigDataEnvironmentPostProcessor}, which loads {@code application-prod.yml}. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
