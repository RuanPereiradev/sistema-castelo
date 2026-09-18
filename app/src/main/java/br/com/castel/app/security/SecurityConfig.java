package br.com.castel.app.security;

import br.com.castel.app.web.ApiErrorCode;
import br.com.castel.app.web.ProblemResponse;
import br.com.castel.identity.api.IdentitySecurityConfigurer;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/**
 * Composition of the HTTP security chain: stateless, JWT-authenticated, CORS restricted per
 * profile. Bearer token authentication comes from {@code identity} through
 * {@link IdentitySecurityConfigurer}; no authentication rule is decided here.
 *
 * <p>The {@code ERROR} dispatch is permitted: it only happens after the original request already
 * went through this chain, and requiring authentication on it again would turn every 400, 404 or
 * 500 into a 401.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_ROUTES = {
        "/api/auth/login", "/api/auth/refresh", "/actuator/health"
    };

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            IdentitySecurityConfigurer identitySecurityConfigurer,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_ROUTES).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(this::commenceUnauthenticated)
                        .accessDeniedHandler(this::denyAccess));
        identitySecurityConfigurer.applyTo(http);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void commenceUnauthenticated(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        writeProblem(request, response, ApiErrorCode.AUTHENTICATION_REQUIRED,
                "Authentication is required to access this resource");
    }

    private void denyAccess(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        writeProblem(request, response, ApiErrorCode.ACCESS_DENIED,
                "You do not have permission to access this resource");
    }

    /**
     * Written by hand because these two run in the filter chain, outside any
     * {@code @RestControllerAdvice}. The shape comes from {@link ProblemResponse}, so the body
     * carries {@code instance} like every other error of the API.
     */
    private void writeProblem(HttpServletRequest request, HttpServletResponse response, ApiErrorCode errorCode, String detail)
            throws IOException {
        ProblemDetail problemDetail = ProblemResponse.of(request, errorCode, detail);
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}
