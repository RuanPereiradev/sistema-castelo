package br.com.castel.app.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds the single shape of error body the API answers: RFC 7807 with a stable {@code code} and
 * {@code instance} set to the path of the request.
 *
 * <p>{@code type}, {@code title} and {@code status} come from {@link ProblemDetail} itself, so
 * every body carries the six fields the contract requires. Kept in one place because three
 * different layers write error bodies: this module's exception handler, the request body size
 * filter, and the security chain's entry point.
 */
public final class ProblemResponse {

    /** Property name of the stable error code inside the problem body. */
    public static final String CODE_PROPERTY = "code";

    /** Property name of the identifier that ties a 500 body to its stack trace in the log. */
    public static final String CORRELATION_ID_PROPERTY = "correlationId";

    /** Property name of the list of invalid fields inside a validation problem body. */
    public static final String ERRORS_PROPERTY = "errors";

    /**
     * Value of {@code type} in every error body.
     *
     * <p>RFC 9457 lets {@code type} be absent and assumes {@code about:blank} then, and Spring 7
     * leaves it null by default, which drops the field from the JSON. It is set explicitly so the
     * shape of an error body never varies: the front parses the same six fields every time.
     */
    public static final URI BLANK_TYPE = URI.create("about:blank");

    private ProblemResponse() {
    }

    public static ProblemDetail of(HttpServletRequest request, HttpStatus status, String code, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(BLANK_TYPE);
        problemDetail.setProperty(CODE_PROPERTY, code);
        problemDetail.setInstance(instanceOf(request));
        return problemDetail;
    }

    public static ProblemDetail of(HttpServletRequest request, ApiErrorCode errorCode, String detail) {
        return of(request, errorCode.status(), errorCode.code(), detail);
    }

    /**
     * The path of the current request, used as {@code instance}.
     *
     * <p>Only the path: a query string can carry a token or a document number, and the error body
     * goes to logs and to the browser console.
     */
    public static URI instanceOf(HttpServletRequest request) {
        return URI.create(request.getRequestURI());
    }
}
