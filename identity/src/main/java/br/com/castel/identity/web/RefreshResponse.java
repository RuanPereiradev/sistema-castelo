package br.com.castel.identity.web;

/** Response of {@code POST /api/auth/refresh}. */
public class RefreshResponse {

    private final String accessToken;
    private final long expiresIn;

    public RefreshResponse(String accessToken, long expiresIn) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }
}
