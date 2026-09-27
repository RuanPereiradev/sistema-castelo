package br.com.castel.identity.infra;

import br.com.castel.identity.api.AccessTokenAuthenticator;
import br.com.castel.identity.application.AuthenticationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Answers {@link AccessTokenAuthenticator} with the same {@link AuthenticationService} call and the
 * same authorities as {@link JwtAuthenticationFilter}; only the transport of the failure differs,
 * which is the caller's.
 */
@Component
public class JwtAccessTokenAuthenticator implements AccessTokenAuthenticator {

    private final AuthenticationService authenticationService;

    public JwtAccessTokenAuthenticator(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    public Authentication authenticate(String accessToken) {
        return new AccessTokenAuthentication(authenticationService.authenticateAccessToken(accessToken));
    }
}
