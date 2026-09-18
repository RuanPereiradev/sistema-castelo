package br.com.castel.app.web;

import br.com.castel.sharedkernel.ConflictException;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every failure the API can produce into one shape: RFC 7807 with a stable {@code code}.
 *
 * <p>Scoped to no package, so it covers every module. {@code identity}'s
 * {@code AuthExceptionHandler} is scoped to its own package and ordered ahead of this one, so the
 * authentication codes of task 0.4 keep answering their own statuses; this handler is the last
 * word, ordered at {@link Ordered#LOWEST_PRECEDENCE}.
 *
 * <p>Two design points worth keeping:
 *
 * <ul>
 *   <li>{@link AccessDeniedException} and {@link AuthenticationException} are handled explicitly.
 *       They are raised by method security <em>inside</em> a controller call, so they reach this
 *       resolver instead of the security chain's handlers; without an explicit mapping the
 *       catch-all would turn a 403 into a 500. The codes are the same the chain writes.
 *   <li>Nothing about an unexpected failure reaches the caller. The 500 body carries a random
 *       {@code correlationId}, and the same identifier goes to the log with the stack trace.
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    static final String REQUEST_BODY_TOO_LARGE_DETAIL = "Request body is too large";
    static final String INTERNAL_ERROR_DETAIL = "Unexpected internal error";
    static final String MALFORMED_REQUEST_DETAIL = "Request body is missing or malformed";
    static final String VALIDATION_FAILED_DETAIL = "One or more fields are invalid";

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * A rule of the domain broken by the content of an otherwise valid request.
     *
     * <p>Declared for the base type: a subclass that is a {@link NotFoundException} or a
     * {@link ConflictException} matches the more specific handler below, so a module never has to
     * register anything here to get its own status.
     */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainRuleViolation(DomainException exception, HttpServletRequest request) {
        return ProblemResponse.of(
                request, HttpStatus.UNPROCESSABLE_CONTENT, exception.code(), exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException exception, HttpServletRequest request) {
        return ProblemResponse.of(request, HttpStatus.CONFLICT, exception.code(), exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException exception, HttpServletRequest request) {
        return ProblemResponse.of(request, HttpStatus.NOT_FOUND, exception.code(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidRequestBody(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<InvalidField> invalidFields = new ArrayList<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            invalidFields.add(new InvalidField(fieldError.getField(), ValidationRuleCode.of(fieldError.getCode())));
        }
        for (ObjectError objectError : exception.getBindingResult().getGlobalErrors()) {
            invalidFields.add(
                    new InvalidField(objectError.getObjectName(), ValidationRuleCode.of(objectError.getCode())));
        }
        return validationFailed(request, invalidFields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<InvalidField> invalidFields = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            invalidFields.add(new InvalidField(lastNodeOf(violation), ruleCodeOf(violation)));
        }
        return validationFailed(request, invalidFields);
    }

    /**
     * An unreadable body.
     *
     * <p>Logs nothing: the parser's message echoes the offending token, and an unquoted
     * {@code "password":secret} would put the password in the log. The detail is generic for the
     * same reason.
     *
     * <p>A body that went past the size limit while being read (no {@code Content-Length},
     * chunked transfer) arrives here wrapped, and answers 413 instead of 400: it is the same limit,
     * caught during the read rather than at the header.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableRequestBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        if (NestedExceptionUtils.getMostSpecificCause(exception) instanceof RequestBodyTooLargeException) {
            return ProblemResponse.of(request, ApiErrorCode.REQUEST_BODY_TOO_LARGE, REQUEST_BODY_TOO_LARGE_DETAIL);
        }
        return ProblemResponse.of(request, ApiErrorCode.MALFORMED_REQUEST, MALFORMED_REQUEST_DETAIL);
    }

    /** The size limit reached a handler without being wrapped by a message converter. */
    @ExceptionHandler(RequestBodyTooLargeException.class)
    public ProblemDetail handleRequestBodyTooLarge(HttpServletRequest request) {
        return ProblemResponse.of(request, ApiErrorCode.REQUEST_BODY_TOO_LARGE, REQUEST_BODY_TOO_LARGE_DETAIL);
    }

    /**
     * No route, and no static resource, answers this path.
     *
     * <p>{@link NoHandlerFoundException} is mapped alongside {@link NoResourceFoundException}
     * because which of the two is raised depends on whether static resource handling is mapped, and
     * the answer to the caller must not.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ProblemDetail handleUnknownRoute(HttpServletRequest request) {
        return ProblemResponse.of(request, ApiErrorCode.RESOURCE_NOT_FOUND, "No resource answers this path");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemResponse.of(
                request, ApiErrorCode.METHOD_NOT_ALLOWED, "This resource does not accept " + exception.getMethod());
        HttpHeaders headers = new HttpHeaders();
        if (exception.getSupportedHttpMethods() != null) {
            headers.setAllow(exception.getSupportedHttpMethods());
        }
        return new ResponseEntity<>(problemDetail, headers, ApiErrorCode.METHOD_NOT_ALLOWED.status());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationRequired(HttpServletRequest request) {
        return ProblemResponse.of(
                request,
                ApiErrorCode.AUTHENTICATION_REQUIRED,
                "Authentication is required to access this resource");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(HttpServletRequest request) {
        return ProblemResponse.of(
                request, ApiErrorCode.ACCESS_DENIED, "You do not have permission to access this resource");
    }

    /**
     * Anything not mapped above.
     *
     * <p>The body says only that something failed, plus the {@code correlationId} the operator can
     * read to us over the phone. Exception message, class name, SQL and file path stay in the log.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedFailure(Exception exception, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        LOGGER.error(
                "Unexpected failure handling {} {} correlationId={}",
                request.getMethod(),
                request.getRequestURI(),
                correlationId,
                exception);
        ProblemDetail problemDetail =
                ProblemResponse.of(request, ApiErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_DETAIL);
        problemDetail.setProperty(ProblemResponse.CORRELATION_ID_PROPERTY, correlationId);
        return problemDetail;
    }

    private static ProblemDetail validationFailed(HttpServletRequest request, List<InvalidField> invalidFields) {
        ProblemDetail problemDetail =
                ProblemResponse.of(request, ApiErrorCode.VALIDATION_FAILED, VALIDATION_FAILED_DETAIL);
        problemDetail.setProperty(ProblemResponse.ERRORS_PROPERTY, List.copyOf(invalidFields));
        return problemDetail;
    }

    private static String lastNodeOf(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastSeparator = path.lastIndexOf('.');
        return lastSeparator < 0 ? path : path.substring(lastSeparator + 1);
    }

    private static String ruleCodeOf(ConstraintViolation<?> violation) {
        if (violation.getConstraintDescriptor() == null
                || violation.getConstraintDescriptor().getAnnotation() == null) {
            return ValidationRuleCode.UNKNOWN_RULE_CODE;
        }
        return ValidationRuleCode.of(
                violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
    }
}
