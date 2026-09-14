package br.com.castel.identity.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import br.com.castel.identity.api.Role;
import br.com.castel.identity.application.InvalidTokenException;
import br.com.castel.identity.application.IssuedTokenPair;
import br.com.castel.identity.application.TokenClaims;
import br.com.castel.identity.application.TokenExpiredException;
import br.com.castel.identity.application.TokenType;
import br.com.castel.identity.domain.User;
import br.com.castel.sharedkernel.support.MutableClock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Written from docs/task-0.4-identity-auth.md and the decisions of task 0.4 (HS256, 256-bit
 * secret, claims {@code sub, roles, tokenVersion, type, iat, exp}, access 15 min, refresh 7 days),
 * not from the implementation.
 */
class JwtTokenIssuerTest {

    private static final String SECRET = "test-only-jwt-secret-with-more-than-256-bits-0123456789abcdef";
    private static final String OTHER_SECRET = "another-jwt-secret-also-longer-than-256-bits-fedcba9876543210";
    private static final Instant ISSUED_AT = Instant.parse("2026-09-13T12:00:00Z");
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    private static final Duration REFRESH_LIFETIME = Duration.ofDays(7);
    private static final String FULL_NAME = "Joao da Silva";
    private static final String USERNAME = "joao.silva";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private MutableClock clock;
    private JwtTokenIssuer issuer;
    private User user;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(ISSUED_AT);
        issuer = new JwtTokenIssuer(SECRET, clock);
        user = User.create(
                UUID.randomUUID(),
                USERNAME,
                FULL_NAME,
                "Valid-Password-123",
                Set.of(Role.WAITER, Role.FRONT_DESK),
                new BCryptPasswordEncoder(4));
    }

    private JsonNode payloadOf(String token) {
        String payloadSegment = token.split("\\.")[1];
        byte[] payload = Base64.getUrlDecoder().decode(payloadSegment);
        return jsonMapper.readTree(payload);
    }

    @Nested
    class AccessTokenExpiration {

        @Test
        void shouldAcceptAccessTokenOneSecondBeforeItExpires() {
            String accessToken = issuer.issueAccessToken(user);
            clock.setInstant(ISSUED_AT.plus(ACCESS_LIFETIME).minusSeconds(1));

            TokenClaims claims = issuer.parseAndValidate(accessToken, TokenType.ACCESS);

            assertThat(claims.userId()).isEqualTo(user.id());
        }

        @Test
        void shouldAcceptAccessTokenAtTheExactExpirationSecond() {
            String accessToken = issuer.issueAccessToken(user);
            clock.setInstant(ISSUED_AT.plus(ACCESS_LIFETIME));

            TokenClaims claims = issuer.parseAndValidate(accessToken, TokenType.ACCESS);

            assertThat(claims.userId()).isEqualTo(user.id());
        }

        @Test
        void shouldRejectAccessTokenOneSecondAfterItExpiresWithTokenExpired() {
            String accessToken = issuer.issueAccessToken(user);
            clock.setInstant(ISSUED_AT.plus(ACCESS_LIFETIME).plusSeconds(1));

            assertThatThrownBy(() -> issuer.parseAndValidate(accessToken, TokenType.ACCESS))
                    .asInstanceOf(type(TokenExpiredException.class))
                    .extracting(TokenExpiredException::code)
                    .isEqualTo("TOKEN_EXPIRED");
        }

        @Test
        void shouldSetAccessTokenExpirationFifteenMinutesAfterIssuance() {
            String accessToken = issuer.issueAccessToken(user);

            JsonNode payload = payloadOf(accessToken);

            assertThat(payload.path("iat").asLong()).isEqualTo(ISSUED_AT.getEpochSecond());
            assertThat(payload.path("exp").asLong())
                    .isEqualTo(ISSUED_AT.plus(ACCESS_LIFETIME).getEpochSecond());
        }

        @Test
        void shouldReportAccessTokenLifetimeOfFifteenMinutesInSeconds() {
            IssuedTokenPair pair = issuer.issueTokenPair(user);

            assertThat(pair.expiresInSeconds()).isEqualTo(ACCESS_LIFETIME.toSeconds());
        }
    }

    @Nested
    class RefreshTokenExpiration {

        @Test
        void shouldAcceptRefreshTokenOneSecondBeforeSevenDaysElapse() {
            IssuedTokenPair pair = issuer.issueTokenPair(user);
            clock.setInstant(ISSUED_AT.plus(REFRESH_LIFETIME).minusSeconds(1));

            TokenClaims claims = issuer.parseAndValidate(pair.refreshToken(), TokenType.REFRESH);

            assertThat(claims.userId()).isEqualTo(user.id());
        }

        @Test
        void shouldAcceptRefreshTokenAtTheExactExpirationSecond() {
            IssuedTokenPair pair = issuer.issueTokenPair(user);
            clock.setInstant(ISSUED_AT.plus(REFRESH_LIFETIME));

            TokenClaims claims = issuer.parseAndValidate(pair.refreshToken(), TokenType.REFRESH);

            assertThat(claims.userId()).isEqualTo(user.id());
        }

        @Test
        void shouldRejectRefreshTokenOneSecondAfterSevenDaysWithTokenExpired() {
            IssuedTokenPair pair = issuer.issueTokenPair(user);
            clock.setInstant(ISSUED_AT.plus(REFRESH_LIFETIME).plusSeconds(1));

            assertThatThrownBy(() -> issuer.parseAndValidate(pair.refreshToken(), TokenType.REFRESH))
                    .isInstanceOf(TokenExpiredException.class);
        }

        @Test
        void shouldSetRefreshTokenExpirationSevenDaysAfterIssuance() {
            IssuedTokenPair pair = issuer.issueTokenPair(user);

            JsonNode payload = payloadOf(pair.refreshToken());

            assertThat(payload.path("exp").asLong())
                    .isEqualTo(ISSUED_AT.plus(REFRESH_LIFETIME).getEpochSecond());
        }
    }

    @Nested
    class InvalidTokens {

        @Test
        void shouldRejectMalformedTokenWithInvalidToken() {
            assertThatThrownBy(() -> issuer.parseAndValidate("not-a-jwt", TokenType.ACCESS))
                    .asInstanceOf(type(InvalidTokenException.class))
                    .extracting(InvalidTokenException::code)
                    .isEqualTo("INVALID_TOKEN");
        }

        @Test
        void shouldRejectEmptyTokenWithInvalidToken() {
            assertThatThrownBy(() -> issuer.parseAndValidate("", TokenType.ACCESS))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void shouldRejectTokenSignedWithAnotherSecretWithInvalidToken() {
            String foreignToken = new JwtTokenIssuer(OTHER_SECRET, clock).issueAccessToken(user);

            assertThatThrownBy(() -> issuer.parseAndValidate(foreignToken, TokenType.ACCESS))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void shouldRejectTokenWhosePayloadWasTamperedWithInvalidToken() {
            String accessToken = issuer.issueAccessToken(user);
            String[] segments = accessToken.split("\\.");
            String forgedPayload = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8)
                            .replace("WAITER", "ADMIN")
                            .getBytes(StandardCharsets.UTF_8));
            String tamperedToken = segments[0] + "." + forgedPayload + "." + segments[2];

            assertThatThrownBy(() -> issuer.parseAndValidate(tamperedToken, TokenType.ACCESS))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void shouldRejectRefreshTokenUsedAsAccessToken() {
            IssuedTokenPair pair = issuer.issueTokenPair(user);

            assertThatThrownBy(() -> issuer.parseAndValidate(pair.refreshToken(), TokenType.ACCESS))
                    .asInstanceOf(type(InvalidTokenException.class))
                    .extracting(InvalidTokenException::code)
                    .isEqualTo("INVALID_TOKEN");
        }

        @Test
        void shouldRejectAccessTokenUsedAsRefreshToken() {
            IssuedTokenPair pair = issuer.issueTokenPair(user);

            assertThatThrownBy(() -> issuer.parseAndValidate(pair.accessToken(), TokenType.REFRESH))
                    .asInstanceOf(type(InvalidTokenException.class))
                    .extracting(InvalidTokenException::code)
                    .isEqualTo("INVALID_TOKEN");
        }
    }

    @Nested
    class Claims {

        @Test
        void shouldEmitExactlyTheDecidedClaimsInAccessToken() {
            String accessToken = issuer.issueAccessToken(user);

            JsonNode payload = payloadOf(accessToken);

            assertThat(payload.propertyNames())
                    .containsExactlyInAnyOrder("sub", "roles", "tokenVersion", "type", "iat", "exp");
        }

        @Test
        void shouldNotEmitNameUsernameOrEmailInAnyToken() {
            IssuedTokenPair pair = issuer.issueTokenPair(user);

            String accessPayload = payloadOf(pair.accessToken()).toString();
            String refreshPayload = payloadOf(pair.refreshToken()).toString();

            assertThat(accessPayload).doesNotContain(FULL_NAME).doesNotContain(USERNAME).doesNotContain("email");
            assertThat(refreshPayload).doesNotContain(FULL_NAME).doesNotContain(USERNAME).doesNotContain("email");
        }

        @Test
        void shouldEmitExactlyTheDecidedClaimsInRefreshToken() {
            IssuedTokenPair pair = issuer.issueTokenPair(user);

            JsonNode payload = payloadOf(pair.refreshToken());

            assertThat(payload.propertyNames())
                    .containsExactlyInAnyOrder("sub", "tokenVersion", "type", "iat", "exp");
        }

        @Test
        void shouldNotEmitRolesInRefreshToken() {
            IssuedTokenPair pair = issuer.issueTokenPair(user);

            JsonNode payload = payloadOf(pair.refreshToken());

            assertThat(payload.has("roles")).isFalse();
        }

        @Test
        void shouldUseUserIdAsSubject() {
            String accessToken = issuer.issueAccessToken(user);

            JsonNode payload = payloadOf(accessToken);

            assertThat(payload.path("sub").asString()).isEqualTo(user.id().value().toString());
        }

        @Test
        void shouldDistinguishAccessAndRefreshByTypeClaim() {
            IssuedTokenPair pair = issuer.issueTokenPair(user);

            String accessType = payloadOf(pair.accessToken()).path("type").asString();
            String refreshType = payloadOf(pair.refreshToken()).path("type").asString();

            assertThat(accessType).isEqualTo("access");
            assertThat(refreshType).isEqualTo("refresh");
        }

        @Test
        void shouldRoundTripUserIdVersionRolesAndTypeOfAccessToken() {
            user.registerLogin(clock);
            String accessToken = issuer.issueAccessToken(user);

            TokenClaims claims = issuer.parseAndValidate(accessToken, TokenType.ACCESS);

            assertThat(claims.userId()).isEqualTo(user.id());
            assertThat(claims.tokenVersion()).isEqualTo(user.tokenVersion());
            assertThat(claims.roles()).containsExactlyInAnyOrder(Role.WAITER, Role.FRONT_DESK);
            assertThat(claims.type()).isEqualTo(TokenType.ACCESS);
        }

        @Test
        void shouldKeepTokenVersionOfIssuanceMomentAfterUserVersionChanges() {
            int versionAtIssuance = user.tokenVersion();
            String accessToken = issuer.issueAccessToken(user);
            user.revokeSessions();

            TokenClaims claims = issuer.parseAndValidate(accessToken, TokenType.ACCESS);

            assertThat(claims.tokenVersion()).isEqualTo(versionAtIssuance);
        }
    }

    /** Builds a token signed with {@code secret}, bypassing the issuer, to control each claim. */
    private String forgeToken(String secret, Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .issuedAt(Date.from(ISSUED_AT))
                .expiration(Date.from(ISSUED_AT.plus(ACCESS_LIFETIME)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    private Map<String, Object> accessClaimsWithType(String typeClaim) {
        return Map.of(
                "sub", user.id().value().toString(),
                "tokenVersion", user.tokenVersion(),
                "roles", List.of("WAITER"),
                "type", typeClaim);
    }

    @Nested
    class TypeClaim {

        @Test
        void shouldAcceptForgedTokenWithLowercaseAccessTypeSignedWithTheRightSecret() {
            String token = forgeToken(SECRET, accessClaimsWithType("access"));

            TokenClaims claims = issuer.parseAndValidate(token, TokenType.ACCESS);

            assertThat(claims.type()).isEqualTo(TokenType.ACCESS);
        }

        @Test
        void shouldRejectUppercaseAccessTypeWithInvalidTokenEvenWhenSignatureIsValid() {
            String token = forgeToken(SECRET, accessClaimsWithType("ACCESS"));

            assertThatThrownBy(() -> issuer.parseAndValidate(token, TokenType.ACCESS))
                    .asInstanceOf(type(InvalidTokenException.class))
                    .extracting(InvalidTokenException::code)
                    .isEqualTo("INVALID_TOKEN");
        }

        @Test
        void shouldRejectCapitalizedRefreshTypeWithInvalidToken() {
            Map<String, Object> claims = Map.of(
                    "sub", user.id().value().toString(), "tokenVersion", user.tokenVersion(), "type", "Refresh");
            String token = forgeToken(SECRET, claims);

            assertThatThrownBy(() -> issuer.parseAndValidate(token, TokenType.REFRESH))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void shouldRejectTokenWithoutTypeClaimWithInvalidToken() {
            Map<String, Object> claims = Map.of(
                    "sub", user.id().value().toString(), "tokenVersion", user.tokenVersion(), "roles", List.of("WAITER"));
            String token = forgeToken(SECRET, claims);

            assertThatThrownBy(() -> issuer.parseAndValidate(token, TokenType.ACCESS))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void shouldRejectTokenWithoutSubjectWithInvalidToken() {
            Map<String, Object> claims = Map.of(
                    "tokenVersion", user.tokenVersion(), "roles", List.of("WAITER"), "type", "access");
            String token = forgeToken(SECRET, claims);

            assertThatThrownBy(() -> issuer.parseAndValidate(token, TokenType.ACCESS))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void shouldRejectTokenWithoutTokenVersionWithInvalidToken() {
            Map<String, Object> claims = Map.of(
                    "sub", user.id().value().toString(), "roles", List.of("WAITER"), "type", "access");
            String token = forgeToken(SECRET, claims);

            assertThatThrownBy(() -> issuer.parseAndValidate(token, TokenType.ACCESS))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Nested
    class SecretRequirements {

        private static final String SECRET_WITH_31_ASCII_BYTES = "s".repeat(31);
        private static final String SECRET_WITH_32_ASCII_BYTES = "s".repeat(32);

        @Test
        void shouldRejectSecretWithThirtyOneBytes() {
            assertThatThrownBy(() -> new JwtTokenIssuer(SECRET_WITH_31_ASCII_BYTES, clock))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void shouldNotExposeRejectedSecretInExceptionMessage() {
            String distinctiveShortSecret = "Leak-Canary-Secret-31-bytes-xyz";

            Throwable thrown = catchThrowable(() -> new JwtTokenIssuer(distinctiveShortSecret, clock));

            assertThat(distinctiveShortSecret.getBytes(StandardCharsets.UTF_8)).hasSize(31);
            assertThat(thrown).isInstanceOf(IllegalStateException.class);
            assertThat(String.valueOf(thrown.getMessage())).doesNotContain(distinctiveShortSecret);
            assertThat(thrown.toString()).doesNotContain(distinctiveShortSecret);
        }

        @Test
        void shouldAcceptSecretWithExactlyThirtyTwoBytes() {
            JwtTokenIssuer minimalIssuer = new JwtTokenIssuer(SECRET_WITH_32_ASCII_BYTES, clock);

            TokenClaims claims = minimalIssuer.parseAndValidate(minimalIssuer.issueAccessToken(user), TokenType.ACCESS);

            assertThat(claims.userId()).isEqualTo(user.id());
        }

        @Test
        void shouldMeasureSecretInUtf8BytesAcceptingSixteenTwoByteCharacters() {
            String multibyteSecret = "ç".repeat(16);

            JwtTokenIssuer multibyteIssuer = new JwtTokenIssuer(multibyteSecret, clock);

            assertThat(multibyteIssuer.parseAndValidate(multibyteIssuer.issueAccessToken(user), TokenType.ACCESS).userId())
                    .isEqualTo(user.id());
        }

        @Test
        void shouldMeasureSecretInUtf8BytesRejectingThirtyOneBytesWithSixteenCharacters() {
            String multibyteSecret = "ç".repeat(15) + "a";

            assertThatThrownBy(() -> new JwtTokenIssuer(multibyteSecret, clock))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void shouldRejectEmptySecret() {
            assertThatThrownBy(() -> new JwtTokenIssuer("", clock)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void shouldRejectNullSecret() {
            assertThatThrownBy(() -> new JwtTokenIssuer(null, clock)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void shouldRejectSecretOfThirtyTwoSpacesEvenThoughItHasEnoughBytes() {
            String thirtyTwoSpaces = " ".repeat(32);

            assertThatThrownBy(() -> new JwtTokenIssuer(thirtyTwoSpaces, clock))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void shouldRejectLongSecretMadeOnlyOfWhitespace() {
            String whitespaceOnly = " \t".repeat(40);

            assertThatThrownBy(() -> new JwtTokenIssuer(whitespaceOnly, clock))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void shouldNotExposeBlankSecretInExceptionMessage() {
            String thirtyTwoSpaces = " ".repeat(32);

            Throwable thrown = catchThrowable(() -> new JwtTokenIssuer(thirtyTwoSpaces, clock));

            assertThat(thrown).isInstanceOf(IllegalStateException.class);
            assertThat(String.valueOf(thrown.getMessage())).doesNotContain(thirtyTwoSpaces);
        }
    }

    /** Decision #67: at least 32 UTF-8 bytes after trim(); the signature uses the exact configured value. */
    @Nested
    class SecretMeasuredAfterTrim {

        private static final String THIRTY_TWO_BYTES = "k".repeat(32);
        private static final String PADDED_THIRTY_TWO_BYTES = "   " + THIRTY_TWO_BYTES + "   ";

        private String subjectVerifiedWithExactKey(String token, String exactSecret) {
            return Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(exactSecret.getBytes(StandardCharsets.UTF_8)))
                    .clock(() -> Date.from(ISSUED_AT))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        }

        @Test
        void shouldRejectThirtyOneSpacesFollowedByOneCharacter() {
            String secret = " ".repeat(31) + "x";

            assertThatThrownBy(() -> new JwtTokenIssuer(secret, clock)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void shouldNotExposePaddedShortSecretInExceptionMessage() {
            String canary = "Leak_Canary_Trimmed_0123456789a";
            String padded = "      " + canary + "      ";

            Throwable thrown = catchThrowable(() -> new JwtTokenIssuer(padded, clock));

            assertThat(canary.getBytes(StandardCharsets.UTF_8)).hasSize(31);
            assertThat(thrown).isInstanceOf(IllegalStateException.class);
            assertThat(String.valueOf(thrown.getMessage())).doesNotContain("Leak_Canary_Trimmed");
            assertThat(thrown.toString()).doesNotContain("Leak_Canary_Trimmed");
        }

        @Test
        void shouldRejectThirtyOneMultibyteBytesSurroundedBySpaces() {
            String secret = "  " + "ç".repeat(15) + "a" + "  ";

            assertThatThrownBy(() -> new JwtTokenIssuer(secret, clock)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void shouldAcceptThirtyTwoBytesSurroundedBySpaces() {
            JwtTokenIssuer paddedIssuer = new JwtTokenIssuer(PADDED_THIRTY_TWO_BYTES, clock);

            TokenClaims claims = paddedIssuer.parseAndValidate(paddedIssuer.issueAccessToken(user), TokenType.ACCESS);

            assertThat(claims.userId()).isEqualTo(user.id());
        }

        @Test
        void shouldValidateTokenWithAnotherIssuerConfiguredWithTheSamePaddedSecret() {
            String token = new JwtTokenIssuer(PADDED_THIRTY_TWO_BYTES, clock).issueAccessToken(user);

            TokenClaims claims = new JwtTokenIssuer(PADDED_THIRTY_TWO_BYTES, clock).parseAndValidate(token, TokenType.ACCESS);

            assertThat(claims.userId()).isEqualTo(user.id());
        }

        @Test
        void shouldSignWithExactConfiguredSecretIncludingSurroundingSpaces() {
            String token = new JwtTokenIssuer(PADDED_THIRTY_TWO_BYTES, clock).issueAccessToken(user);

            String subject = subjectVerifiedWithExactKey(token, PADDED_THIRTY_TWO_BYTES);

            assertThat(subject).isEqualTo(user.id().value().toString());
        }

        @Test
        void shouldNotValidateTokenSignedWithTrimmedSecretWhenConfiguredSecretHasSurroundingSpaces() {
            String tokenOfTrimmedSecret = new JwtTokenIssuer(THIRTY_TWO_BYTES, clock).issueAccessToken(user);
            JwtTokenIssuer paddedIssuer = new JwtTokenIssuer(PADDED_THIRTY_TWO_BYTES, clock);

            assertThatThrownBy(() -> paddedIssuer.parseAndValidate(tokenOfTrimmedSecret, TokenType.ACCESS))
                    .asInstanceOf(type(InvalidTokenException.class))
                    .extracting(InvalidTokenException::code)
                    .isEqualTo("INVALID_TOKEN");
        }

        @Test
        void shouldNotValidateTokenSignedWithPaddedSecretWhenConfiguredSecretIsTrimmed() {
            String tokenOfPaddedSecret = new JwtTokenIssuer(PADDED_THIRTY_TWO_BYTES, clock).issueAccessToken(user);
            JwtTokenIssuer trimmedIssuer = new JwtTokenIssuer(THIRTY_TWO_BYTES, clock);

            assertThatThrownBy(() -> trimmedIssuer.parseAndValidate(tokenOfPaddedSecret, TokenType.ACCESS))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Nested
    class ExpirationClaim {

        @Test
        void shouldRejectTokenWithoutExpirationWithInvalidToken() {
            String token = Jwts.builder()
                    .claims(accessClaimsWithType("access"))
                    .issuedAt(Date.from(ISSUED_AT))
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                    .compact();

            assertThatThrownBy(() -> issuer.parseAndValidate(token, TokenType.ACCESS))
                    .asInstanceOf(type(InvalidTokenException.class))
                    .extracting(InvalidTokenException::code)
                    .isEqualTo("INVALID_TOKEN");
        }

        @Test
        void shouldRejectRefreshTokenWithoutExpirationWithInvalidToken() {
            Map<String, Object> claims = Map.of(
                    "sub", user.id().value().toString(), "tokenVersion", user.tokenVersion(), "type", "refresh");
            String token = Jwts.builder()
                    .claims(claims)
                    .issuedAt(Date.from(ISSUED_AT))
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                    .compact();

            assertThatThrownBy(() -> issuer.parseAndValidate(token, TokenType.REFRESH))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void shouldAcceptForgedTokenAtItsExactExpirationSecond() {
            String token = forgeToken(SECRET, accessClaimsWithType("access"));
            clock.setInstant(ISSUED_AT.plus(ACCESS_LIFETIME));

            TokenClaims claims = issuer.parseAndValidate(token, TokenType.ACCESS);

            assertThat(claims.userId()).isEqualTo(user.id());
        }

        @Test
        void shouldRejectForgedTokenOneSecondAfterItsExpirationWithTokenExpired() {
            String token = forgeToken(SECRET, accessClaimsWithType("access"));
            clock.setInstant(ISSUED_AT.plus(ACCESS_LIFETIME).plusSeconds(1));

            assertThatThrownBy(() -> issuer.parseAndValidate(token, TokenType.ACCESS))
                    .asInstanceOf(type(TokenExpiredException.class))
                    .extracting(TokenExpiredException::code)
                    .isEqualTo("TOKEN_EXPIRED");
        }
    }
}
