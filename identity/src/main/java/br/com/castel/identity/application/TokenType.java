package br.com.castel.identity.application;

import java.util.Arrays;
import java.util.Optional;

/** Distinguishes an access token from a refresh token inside a JWT's {@code type} claim. */
public enum TokenType {

    ACCESS("access"),
    REFRESH("refresh");

    private final String claimValue;

    TokenType(String claimValue) {
        this.claimValue = claimValue;
    }

    /** Value written to the {@code type} claim: always lowercase. */
    public String claimValue() {
        return claimValue;
    }

    /**
     * The token type whose {@link #claimValue()} is exactly {@code claimValue}, case-sensitive.
     *
     * @return empty for null, for an unknown value, and for any other casing ({@code "ACCESS"} included)
     */
    public static Optional<TokenType> fromClaimValue(String claimValue) {
        return Arrays.stream(values()).filter(type -> type.claimValue.equals(claimValue)).findFirst();
    }
}
