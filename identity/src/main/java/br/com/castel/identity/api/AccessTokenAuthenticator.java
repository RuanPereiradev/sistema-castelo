package br.com.castel.identity.api;

import org.springframework.security.core.Authentication;

/**
 * Authenticates an access token outside the HTTP filter chain — the {@code CONNECT} frame of the
 * kitchen display's STOMP session (task 3.5) — with exactly the decisions and the roles the bearer
 * filter applies to a request.
 *
 * <p>The token comes from a header, never from a query string: a URL ends up in the logs of every
 * proxy on the way.
 */
public interface AccessTokenAuthenticator {

    /**
     * The authenticated user, as an {@link Authentication} whose principal is an
     * {@link AuthenticatedUser}, whose authorities are the {@code ROLE_*} of its roles and whose
     * {@link Authentication#getName() name} is the id of the user.
     *
     * @param accessToken the compact token, without the {@code Bearer } prefix
     * @throws br.com.castel.sharedkernel.DomainException with code {@code TOKEN_EXPIRED},
     *     {@code INVALID_TOKEN}, {@code USER_INACTIVE} or {@code SESSION_SUPERSEDED}
     */
    Authentication authenticate(String accessToken);
}
