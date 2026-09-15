package br.com.castel.identity.application;

import br.com.castel.identity.domain.User;

/** Port for issuing and validating the JWTs that carry a {@link User}'s session. */
public interface TokenIssuer {

    /** Issues a fresh access/refresh pair, stamped with the user's current {@code tokenVersion}. */
    IssuedTokenPair issueTokenPair(User user);

    /** Issues a new access token only, used by the refresh flow; does not touch the refresh token. */
    String issueAccessToken(User user);

    /**
     * Decodes and verifies {@code token}, checking signature, expiration and that its
     * {@code type} claim matches {@code expectedType}.
     *
     * @throws TokenExpiredException if the token is well-formed but expired
     * @throws InvalidTokenException if the token is malformed, has an invalid signature, or its
     *         {@code type} claim does not match {@code expectedType}
     */
    TokenClaims parseAndValidate(String token, TokenType expectedType);
}
