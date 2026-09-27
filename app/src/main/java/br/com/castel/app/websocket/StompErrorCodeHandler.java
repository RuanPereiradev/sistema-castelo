package br.com.castel.app.websocket;

import br.com.castel.app.web.ApiErrorCode;
import br.com.castel.sharedkernel.DomainException;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompConversionException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

/**
 * Puts a stable code in the {@code message} header of every {@code ERROR} frame, the same codes the
 * REST API answers: the screen translates it, and nothing of an internal failure reaches the client.
 *
 * <ul>
 *   <li>a refused token: its own code ({@code TOKEN_EXPIRED}, {@code INVALID_TOKEN},
 *       {@code SESSION_SUPERSEDED}, {@code USER_INACTIVE})
 *   <li>no token on {@code CONNECT}, or a frame the protocol refuses on a session that is not
 *       connected (a frame before {@code CONNECT}, a second {@code CONNECT}), which Spring raises as
 *       {@link IllegalStateException}: {@code AUTHENTICATION_REQUIRED}
 *   <li>a subscription or a {@code SEND} the user may not make: {@code ACCESS_DENIED}
 *   <li>a frame that does not parse: {@code MALFORMED_REQUEST}; anything else: {@code INTERNAL_ERROR}
 * </ul>
 */
class StompErrorCodeHandler extends StompSubProtocolErrorHandler {

    @Override
    protected Message<byte[]> handleInternal(
            StompHeaderAccessor errorHeaderAccessor,
            byte[] errorPayload,
            @Nullable Throwable cause,
            @Nullable StompHeaderAccessor clientHeaderAccessor) {
        errorHeaderAccessor.setMessage(codeOf(cause));
        return super.handleInternal(errorHeaderAccessor, errorPayload, cause, clientHeaderAccessor);
    }

    private static String codeOf(@Nullable Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DomainException domainException) {
                return domainException.code();
            }
            if (cause instanceof AccessDeniedException) {
                return ApiErrorCode.ACCESS_DENIED.code();
            }
            if (cause instanceof AuthenticationException || cause instanceof IllegalStateException) {
                return ApiErrorCode.AUTHENTICATION_REQUIRED.code();
            }
            if (cause instanceof StompConversionException) {
                return ApiErrorCode.MALFORMED_REQUEST.code();
            }
        }
        return ApiErrorCode.INTERNAL_ERROR.code();
    }
}
