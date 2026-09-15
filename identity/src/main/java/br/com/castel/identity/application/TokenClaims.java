package br.com.castel.identity.application;

import br.com.castel.identity.api.Role;
import br.com.castel.identity.api.UserId;
import java.util.Set;

/**
 * Claims decoded from a JWT already verified by {@link TokenIssuer#parseAndValidate}.
 *
 * @param roles roles stamped on an access token; always empty for a refresh token, which carries
 *        none (the refresh flow reads the roles from the database)
 */
public record TokenClaims(UserId userId, int tokenVersion, Set<Role> roles, TokenType type) {
}
