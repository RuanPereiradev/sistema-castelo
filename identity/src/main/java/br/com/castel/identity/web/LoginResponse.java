package br.com.castel.identity.web;

/** Response of {@code POST /api/auth/login}. */
public class LoginResponse {

    private final String accessToken;
    private final String refreshToken;
    private final long expiresIn;
    private final UserSummaryResponse user;

    public LoginResponse(String accessToken, String refreshToken, long expiresIn, UserSummaryResponse user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public UserSummaryResponse getUser() {
        return user;
    }
}
