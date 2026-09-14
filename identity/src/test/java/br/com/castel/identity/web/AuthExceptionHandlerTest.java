package br.com.castel.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.identity.application.InvalidCredentialsException;
import br.com.castel.identity.application.InvalidTokenException;
import br.com.castel.identity.application.SessionSupersededException;
import br.com.castel.identity.application.TokenExpiredException;
import br.com.castel.identity.application.TooManyLoginAttemptsException;
import br.com.castel.identity.application.UserInactiveException;
import br.com.castel.identity.domain.InvalidUsernameException;
import br.com.castel.identity.domain.PasswordTooLongException;
import br.com.castel.identity.domain.UserWithoutRolesException;
import br.com.castel.identity.domain.WeakPasswordException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

/**
 * Error code table of docs/task-0.4-identity-auth.md ("Códigos de erro"): HTTP status and stable
 * {@code code} per exception.
 *
 * <p>{@code INVALID_USERNAME}, {@code WEAK_PASSWORD}, {@code PASSWORD_TOO_LONG} and {@code USER_WITHOUT_ROLES} have no
 * endpoint that raises them today, so this is the only place their status is checked. The codes
 * reachable over HTTP are also covered end to end in {@code AuthenticationHttpIntegrationTest}.
 */
class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    private static Object codeOf(ProblemDetail problem) {
        return problem.getProperties().get("code");
    }

    @Test
    void shouldMapInvalidUsernameToUnprocessableContent() {
        ProblemDetail problem = handler.handleInvalidUsername(new InvalidUsernameException("username rule violated"));

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(codeOf(problem)).isEqualTo("INVALID_USERNAME");
    }

    @Test
    void shouldMapWeakPasswordToUnprocessableContent() {
        ProblemDetail problem = handler.handleWeakPassword(new WeakPasswordException());

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(codeOf(problem)).isEqualTo("WEAK_PASSWORD");
    }

    @Test
    void shouldMapPasswordTooLongToUnprocessableContent() {
        ProblemDetail problem = handler.handlePasswordTooLong(new PasswordTooLongException());

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(codeOf(problem)).isEqualTo("PASSWORD_TOO_LONG");
    }

    @Test
    void shouldMapUserWithoutRolesToUnprocessableContent() {
        ProblemDetail problem = handler.handleUserWithoutRoles(new UserWithoutRolesException());

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(codeOf(problem)).isEqualTo("USER_WITHOUT_ROLES");
    }

    @Test
    void shouldMapMalformedRequestToBadRequest() {
        ProblemDetail problem = handler.handleMalformedRequest();

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(codeOf(problem)).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void shouldMapInvalidCredentialsToUnauthorized() {
        ProblemDetail problem = handler.handleInvalidCredentials(new InvalidCredentialsException());

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(codeOf(problem)).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void shouldMapUserInactiveToUnauthorized() {
        ProblemDetail problem = handler.handleUserInactive(new UserInactiveException());

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(codeOf(problem)).isEqualTo("USER_INACTIVE");
    }

    @Test
    void shouldMapInvalidTokenToUnauthorized() {
        ProblemDetail problem = handler.handleInvalidToken(new InvalidTokenException());

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(codeOf(problem)).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void shouldMapTokenExpiredToUnauthorized() {
        ProblemDetail problem = handler.handleTokenExpired(new TokenExpiredException());

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(codeOf(problem)).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void shouldMapSessionSupersededToUnauthorized() {
        ProblemDetail problem = handler.handleSessionSuperseded(new SessionSupersededException());

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(codeOf(problem)).isEqualTo("SESSION_SUPERSEDED");
    }

    @Test
    void shouldMapTooManyLoginAttemptsToTooManyRequests() {
        ProblemDetail problem = handler.handleTooManyLoginAttempts(
                new TooManyLoginAttemptsException(TooManyLoginAttemptsException.Reason.FAILURE_LIMIT_REACHED));

        assertThat(problem.getStatus()).isEqualTo(429);
        assertThat(codeOf(problem)).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
    }
}
