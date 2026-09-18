package br.com.castel.app.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Refuses any request whose body goes past the configured limit, for the whole API.
 *
 * <p>Runs before the security chain, so an oversized body is dropped before a password is hashed or
 * a token is parsed. The body is never held in memory:
 *
 * <ul>
 *   <li>when the request declares a {@code Content-Length} above the limit, the answer is written
 *       without reading a single byte of the body;
 *   <li>otherwise the request is wrapped, and the first byte past the limit fails the read (see
 *       {@link SizeLimitedServletInputStream}).
 * </ul>
 *
 * <p>The answer is written here instead of thrown, because at this point in the chain no
 * {@code @RestControllerAdvice} is in play yet.
 */
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private final long maximumBytes;
    private final ObjectMapper objectMapper;

    public RequestBodySizeLimitFilter(long maximumBytes, ObjectMapper objectMapper) {
        this.maximumBytes = maximumBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maximumBytes) {
            writeRequestBodyTooLarge(request, response);
            return;
        }
        filterChain.doFilter(new SizeLimitedHttpServletRequest(request, maximumBytes), response);
    }

    private void writeRequestBodyTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemDetail problemDetail = ProblemResponse.of(
                request, ApiErrorCode.REQUEST_BODY_TOO_LARGE, GlobalExceptionHandler.REQUEST_BODY_TOO_LARGE_DETAIL);
        response.setStatus(ApiErrorCode.REQUEST_BODY_TOO_LARGE.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}
