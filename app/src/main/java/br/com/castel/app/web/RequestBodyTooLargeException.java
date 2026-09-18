package br.com.castel.app.web;

import java.io.IOException;
import java.io.Serial;

/**
 * Thrown while reading a request body that goes past the configured limit.
 *
 * <p>An {@link IOException} on purpose: it is raised from inside
 * {@link jakarta.servlet.ServletInputStream#read()}, whose contract only allows an
 * {@code IOException}, and the message converters wrap an {@code IOException} from the body into a
 * {@code HttpMessageNotReadableException}, which
 * {@link GlobalExceptionHandler} unwraps back into a 413.
 *
 * <p>Carries no size and no excerpt of the body: the point of the limit is to never hold the body
 * in memory.
 */
public class RequestBodyTooLargeException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RequestBodyTooLargeException(long maximumBytes) {
        super("Request body exceeds the limit of " + maximumBytes + " bytes");
    }
}
