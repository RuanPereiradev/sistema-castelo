package br.com.castel.identity.api;

import java.util.Optional;

/** Gives any module access to the user authenticated on the current request. */
public interface CurrentUserProvider {

    /**
     * The user authenticated on the current request.
     *
     * @return empty when the request carries no valid access token (a public route, for instance)
     */
    Optional<AuthenticatedUser> currentUser();
}
