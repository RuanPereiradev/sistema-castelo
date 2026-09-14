package br.com.castel.identity.api;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Lets the composition root install identity's bearer token authentication on its security
 * filter chain without depending on how {@code identity} implements it.
 */
public interface IdentitySecurityConfigurer {

    /**
     * Adds bearer access token authentication to {@code http}.
     *
     * <p>Touches nothing else: authorization rules, CORS, CSRF, session policy and the
     * entry point stay with the caller.
     */
    void applyTo(HttpSecurity http);
}
