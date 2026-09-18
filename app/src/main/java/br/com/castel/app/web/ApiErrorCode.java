package br.com.castel.app.web;

import org.springframework.http.HttpStatus;

/**
 * Every error code the framework layer itself emits, paired with the single HTTP status it answers.
 *
 * <p>Pairing code and status in one enum is what enforces the invariant "two different failures
 * never share a code with a different status": the pair exists in exactly one place, and a handler
 * cannot invent a status for a code. Codes that come from a {@code DomainException} are not listed
 * here; those belong to the module that throws them.
 *
 * <p>{@link #AUTHENTICATION_REQUIRED} and {@link #ACCESS_DENIED} repeat the codes the security
 * chain already writes (task 0.4 contract), because a {@code AccessDeniedException} raised by
 * method security inside a controller is resolved here and not by the chain's handlers.
 */
public enum ApiErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    REQUEST_BODY_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ApiErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    /** The stable string the front end translates; always identical to the constant name. */
    public String code() {
        return name();
    }
}
