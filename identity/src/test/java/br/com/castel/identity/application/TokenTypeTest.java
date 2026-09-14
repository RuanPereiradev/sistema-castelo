package br.com.castel.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The {@code type} claim is lowercase and compared exactly (docs/task-0.4-identity-auth.md). */
class TokenTypeTest {

    @Test
    void shouldWriteAccessClaimInLowercase() {
        assertThat(TokenType.ACCESS.claimValue()).isEqualTo("access");
    }

    @Test
    void shouldWriteRefreshClaimInLowercase() {
        assertThat(TokenType.REFRESH.claimValue()).isEqualTo("refresh");
    }

    @Test
    void shouldReadLowercaseAccessClaim() {
        assertThat(TokenType.fromClaimValue("access")).contains(TokenType.ACCESS);
    }

    @Test
    void shouldReadLowercaseRefreshClaim() {
        assertThat(TokenType.fromClaimValue("refresh")).contains(TokenType.REFRESH);
    }

    @Test
    void shouldNotReadUppercaseAccessClaim() {
        assertThat(TokenType.fromClaimValue("ACCESS")).isEmpty();
    }

    @Test
    void shouldNotReadCapitalizedRefreshClaim() {
        assertThat(TokenType.fromClaimValue("Refresh")).isEmpty();
    }

    @Test
    void shouldNotReadClaimWithSurroundingWhitespace() {
        assertThat(TokenType.fromClaimValue(" access")).isEmpty();
    }

    @Test
    void shouldNotReadUnknownClaim() {
        assertThat(TokenType.fromClaimValue("id")).isEmpty();
    }
}
