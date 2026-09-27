package br.com.castel.identity.infra;

import br.com.castel.identity.api.AuthenticatedUser;
import br.com.castel.identity.api.Role;
import java.io.Serial;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * A user authenticated by an access token outside the HTTP filter: the principal is the
 * {@link AuthenticatedUser}, the authorities are the {@code ROLE_*} of its roles, and the name is the
 * id of the user — what a STOMP session is known by — never the text of the whole record.
 */
final class AccessTokenAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final AuthenticatedUser user;

    AccessTokenAuthentication(AuthenticatedUser user) {
        super(authoritiesOf(user));
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return user;
    }

    /** No credentials are kept once the token is verified. */
    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return user.id().value().toString();
    }

    private static List<GrantedAuthority> authoritiesOf(AuthenticatedUser user) {
        return user.roles().stream()
                .map(Role::springAuthority)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }
}
