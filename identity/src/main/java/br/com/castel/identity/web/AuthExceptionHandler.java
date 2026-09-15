package br.com.castel.identity.web;

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
import br.com.castel.sharedkernel.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates the exceptions this module throws into RFC 7807 responses.
 *
 * <p>Maps every code the module can emit, including those no endpoint triggers yet
 * ({@code INVALID_USERNAME}, {@code WEAK_PASSWORD}, {@code PASSWORD_TOO_LONG},
 * {@code USER_WITHOUT_ROLES}), so the HTTP contract is complete. Those domain validations answer
 * 422: the request is well formed, but its content breaks a rule of the domain.
 *
 * <p>Scoped to {@code br.com.castel.identity.web} on purpose, so it never competes with the
 * project-wide {@code @RestControllerAdvice} that a later task adds for every other module.
 */
@RestControllerAdvice(basePackages = "br.com.castel.identity.web")
public class AuthExceptionHandler {

    static final String MALFORMED_REQUEST_CODE = "MALFORMED_REQUEST";

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        return problemDetail(HttpStatus.UNAUTHORIZED, exception);
    }

    @ExceptionHandler(UserInactiveException.class)
    public ProblemDetail handleUserInactive(UserInactiveException exception) {
        return problemDetail(HttpStatus.UNAUTHORIZED, exception);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException exception) {
        return problemDetail(HttpStatus.UNAUTHORIZED, exception);
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ProblemDetail handleTokenExpired(TokenExpiredException exception) {
        return problemDetail(HttpStatus.UNAUTHORIZED, exception);
    }

    @ExceptionHandler(SessionSupersededException.class)
    public ProblemDetail handleSessionSuperseded(SessionSupersededException exception) {
        return problemDetail(HttpStatus.UNAUTHORIZED, exception);
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ProblemDetail handleTooManyLoginAttempts(TooManyLoginAttemptsException exception) {
        return problemDetail(HttpStatus.TOO_MANY_REQUESTS, exception);
    }

    @ExceptionHandler(InvalidUsernameException.class)
    public ProblemDetail handleInvalidUsername(InvalidUsernameException exception) {
        return problemDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception);
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ProblemDetail handleWeakPassword(WeakPasswordException exception) {
        return problemDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception);
    }

    @ExceptionHandler(PasswordTooLongException.class)
    public ProblemDetail handlePasswordTooLong(PasswordTooLongException exception) {
        return problemDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception);
    }

    @ExceptionHandler(UserWithoutRolesException.class)
    public ProblemDetail handleUserWithoutRoles(UserWithoutRolesException exception) {
        return problemDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception);
    }

    /**
     * An unreadable body (malformed JSON, for instance) on any identity endpoint.
     *
     * <p>Handled here, and not left to Spring MVC's {@code DefaultHandlerExceptionResolver}, because
     * that resolver logs a WARN with the parser's message, and the parser echoes the offending
     * token: an unquoted {@code "password":secret} would put the password in the log. This
     * resolver runs first and logs nothing. The detail is generic and never includes the parser's
     * message, for the same reason.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedRequest() {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
        problemDetail.setProperty("code", MALFORMED_REQUEST_CODE);
        return problemDetail;
    }

    private static ProblemDetail problemDetail(HttpStatus status, DomainException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problemDetail.setProperty("code", exception.code());
        return problemDetail;
    }
}
