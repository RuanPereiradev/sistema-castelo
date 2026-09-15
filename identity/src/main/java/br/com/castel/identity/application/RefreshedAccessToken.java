package br.com.castel.identity.application;

/** Outcome of a successful {@link AuthenticationService#refresh(String)} call. */
public record RefreshedAccessToken(String accessToken, long expiresInSeconds) {
}
