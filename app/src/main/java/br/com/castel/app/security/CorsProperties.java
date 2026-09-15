package br.com.castel.app.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origins allowed to call the API from a browser, one list per profile: liberal in {@code dev},
 * an explicit list in {@code prod}. Bound through {@link org.springframework.boot.context.properties.Binder}
 * (not {@code @Value}) so a plain comma-separated environment variable and a YAML list both work.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
