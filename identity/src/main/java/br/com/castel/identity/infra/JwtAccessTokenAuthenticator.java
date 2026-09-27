package br.com.castel.identity.infra;

import br.com.castel.identity.api.AccessTokenAuthenticator;
import br.com.castel.identity.api.AuthenticatedUser;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.application.AuthenticationService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
        AuthenticatedUser user = authenticationService.authenticateAccessToken(accessToken);
        return UsernamePasswordAuthenticationToken.authenticated(
                user,
                null,
                user.roles().stream()
                        .map(Role::springAuthority)
                        .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                        .toList());
    }
}
