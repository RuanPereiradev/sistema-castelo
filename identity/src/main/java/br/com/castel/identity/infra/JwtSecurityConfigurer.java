package br.com.castel.identity.infra;

import br.com.castel.identity.api.IdentitySecurityConfigurer;
import br.com.castel.identity.application.AuthenticationService;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Installs {@link JwtAuthenticationFilter} on the composition root's security filter chain. */
@Component
public class JwtSecurityConfigurer implements IdentitySecurityConfigurer {

    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    public JwtSecurityConfigurer(AuthenticationService authenticationService, ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void applyTo(HttpSecurity http) {
        http.addFilterBefore(
                new JwtAuthenticationFilter(authenticationService, objectMapper),
                UsernamePasswordAuthenticationFilter.class);
    }
}
