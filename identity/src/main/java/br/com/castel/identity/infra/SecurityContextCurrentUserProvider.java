package br.com.castel.identity.infra;

import br.com.castel.identity.api.AuthenticatedUser;
import br.com.castel.identity.api.CurrentUserProvider;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Reads the {@link AuthenticatedUser} that {@link JwtAuthenticationFilter} put in the security context. */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<AuthenticatedUser> currentUser() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getPrincipal)
                .filter(AuthenticatedUser.class::isInstance)
                .map(AuthenticatedUser.class::cast);
    }
}
