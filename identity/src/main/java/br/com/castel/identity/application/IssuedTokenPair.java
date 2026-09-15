package br.com.castel.identity.application;

/** Access and refresh tokens issued together at login. */
public record IssuedTokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
}
