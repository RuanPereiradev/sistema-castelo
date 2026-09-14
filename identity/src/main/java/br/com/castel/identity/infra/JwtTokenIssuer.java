package br.com.castel.identity.infra;

import br.com.castel.identity.application.InvalidTokenException;
import br.com.castel.identity.application.IssuedTokenPair;
import br.com.castel.identity.application.TokenClaims;
import br.com.castel.identity.application.TokenExpiredException;
import br.com.castel.identity.application.TokenIssuer;
import br.com.castel.identity.application.TokenType;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.api.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and validates the HS256-signed JWTs that back a {@link User}'s session.
 *
 * <p>Every token carries {@code sub} (the user id), {@code tokenVersion} and {@code type}
 * ({@code access} or {@code refresh}, lowercase). Only the access token carries {@code roles}: a
 * refresh reads the roles from the database, so removing a role takes effect within one access
 * token lifetime. {@link JwtAuthenticationFilter} enforces single-session-per-user without a
 * server-side session table: it just compares the token's {@code tokenVersion} against the one
 * currently stored on the user.
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    /** Minimum size of {@code app.security.jwt.secret} in UTF-8 bytes: 256 bits, as HS256 requires. */
    public static final int MINIMUM_SECRET_BYTES = 32;

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private static final String CLAIM_TOKEN_VERSION = "tokenVersion";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "type";

    private final SecretKey signingKey;
    private final Clock clock;

    /**
     * @throws IllegalStateException if {@code secret} is missing, blank, or shorter than
     *         {@value #MINIMUM_SECRET_BYTES} bytes in UTF-8 once leading and trailing whitespace is
     *         removed by {@link String#trim()}, which aborts the application boot; the message never
     *         includes the secret
     */
    public JwtTokenIssuer(@Value("${app.security.jwt.secret}") String secret, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(requireStrongSecret(secret));
        this.clock = clock;
    }

    /**
     * Measures the secret after {@code trim()}, so padding with spaces cannot reach the minimum,
     * but signs with the secret exactly as configured: trimming the key itself would silently
     * change it and invalidate every token already issued with it.
     */
    private static byte[] requireStrongSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.security.jwt.secret (JWT_SECRET) must not be blank");
        }
        int meaningfulBytes = secret.trim().getBytes(StandardCharsets.UTF_8).length;
        if (meaningfulBytes < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("app.security.jwt.secret (JWT_SECRET) must have at least "
                    + MINIMUM_SECRET_BYTES + " bytes in UTF-8, not counting leading and trailing whitespace, but has "
                    + meaningfulBytes);
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public IssuedTokenPair issueTokenPair(User user) {
        return new IssuedTokenPair(issueAccessToken(user), issueRefreshToken(user), ACCESS_TOKEN_TTL.toSeconds());
    }

    @Override
    public String issueAccessToken(User user) {
        List<String> roleNames = user.roles().stream().map(Enum::name).toList();
        return tokenFor(user, TokenType.ACCESS, ACCESS_TOKEN_TTL).claim(CLAIM_ROLES, roleNames).compact();
    }

    private String issueRefreshToken(User user) {
        return tokenFor(user, TokenType.REFRESH, REFRESH_TOKEN_TTL).compact();
    }

    private JwtBuilder tokenFor(User user, TokenType type, Duration timeToLive) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .subject(user.id().value().toString())
                .claim(CLAIM_TOKEN_VERSION, user.tokenVersion())
                .claim(CLAIM_TYPE, type.claimValue())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(timeToLive)))
                .signWith(signingKey, Jwts.SIG.HS256);
    }

    @Override
    public TokenClaims parseAndValidate(String token, TokenType expectedType) {
        Claims claims = decode(token);
        requireExpiration(claims);

        TokenType actualType = readType(claims);
        if (actualType != expectedType) {
            throw new InvalidTokenException();
        }

        UserId userId = readUserId(claims);
        int tokenVersion = readTokenVersion(claims);
        Set<Role> roles = actualType == TokenType.ACCESS ? readRoles(claims) : EnumSet.noneOf(Role.class);

        return new TokenClaims(userId, tokenVersion, roles, actualType);
    }

    private Claims decode(String token) {
        try {
            return Jwts.parser()
                    .clock(() -> Date.from(clock.instant()))
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }

    /**
     * Expiration itself is enforced by the jjwt parser in {@link #decode(String)}, which accepts a
     * token up to and including the exact instant of {@code exp}. The parser accepts a token that
     * has no {@code exp} at all, though, and every token issued here carries one, so its absence
     * means the token was not issued here.
     */
    private static void requireExpiration(Claims claims) {
        if (claims.getExpiration() == null) {
            throw new InvalidTokenException();
        }
    }

    private static TokenType readType(Claims claims) {
        Object typeClaim = claims.get(CLAIM_TYPE);
        if (!(typeClaim instanceof String claimValue)) {
            throw new InvalidTokenException();
        }
        return TokenType.fromClaimValue(claimValue).orElseThrow(InvalidTokenException::new);
    }

    private static UserId readUserId(Claims claims) {
        try {
            return UserId.of(claims.getSubject());
        } catch (RuntimeException e) {
            throw new InvalidTokenException();
        }
    }

    private static int readTokenVersion(Claims claims) {
        Integer tokenVersion = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
        if (tokenVersion == null) {
            throw new InvalidTokenException();
        }
        return tokenVersion;
    }

    private static Set<Role> readRoles(Claims claims) {
        List<?> roleNames = claims.get(CLAIM_ROLES, List.class);
        if (roleNames == null) {
            throw new InvalidTokenException();
        }
        try {
            Set<Role> roles = EnumSet.noneOf(Role.class);
            for (Object roleName : roleNames) {
                roles.add(Role.valueOf(String.valueOf(roleName)));
            }
            return roles;
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }
}
