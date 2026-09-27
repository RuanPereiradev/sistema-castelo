package br.com.castel.app.websocket;

import br.com.castel.identity.api.AccessTokenAuthenticator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

/**
 * Authenticates the {@code CONNECT} frame — or its synonym {@code STOMP}, both of message type
 * {@link SimpMessageType#CONNECT} — by its native {@code Authorization: Bearer} header, and
 * records the user on the STOMP session, which every later frame of the session carries.
 *
 * <p>Runs ahead of Spring Security's message interceptors, which read that user. A frame without the
 * header, or a token that {@link AccessTokenAuthenticator} refuses, fails here: the client gets an
 * {@code ERROR} frame with the code (see {@link StompErrorCodeHandler}) and the session closes.
 */
class StompConnectAuthenticationInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenAuthenticator accessTokenAuthenticator;

    StompConnectAuthenticationInterceptor(AccessTokenAuthenticator accessTokenAuthenticator) {
        this.accessTokenAuthenticator = accessTokenAuthenticator;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !SimpMessageType.CONNECT.equals(accessor.getMessageType())) {
            return message;
        }
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new AuthenticationCredentialsNotFoundException("The CONNECT frame carries no bearer token");
        }
        accessor.setUser(accessTokenAuthenticator.authenticate(header.substring(BEARER_PREFIX.length())));
        return message;
    }
}
