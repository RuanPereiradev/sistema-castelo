package br.com.castel.identity.application;

import br.com.castel.identity.domain.User;

/** Outcome of a successful {@link AuthenticationService#login(String, String, String)} call. */
public record AuthenticationResult(User user, IssuedTokenPair tokens) {
}
