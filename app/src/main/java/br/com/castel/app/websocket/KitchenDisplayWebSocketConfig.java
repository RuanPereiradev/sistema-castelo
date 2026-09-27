package br.com.castel.app.websocket;

import br.com.castel.app.security.CorsProperties;
import br.com.castel.identity.api.AccessTokenAuthenticator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over plain WebSocket for the kitchen display (task 3.5), in the same process.
 *
 * <ul>
 *   <li>Endpoint {@code /ws/kitchen}, without SockJS, open to the origins of {@code app.cors}. The HTTP
 *       handshake is not authenticated (the browser's WebSocket API sends no {@code Authorization});
 *       the {@code CONNECT} frame is, by {@link StompConnectAuthenticationInterceptor}.
 *   <li>Simple in-memory broker under {@code /topic}, in publish order, with a 10 s heartbeat both
 *       ways so a screen notices a dead connection. One instance only (v1).
 *   <li>Nothing is accepted from the client but {@code CONNECT}, {@code SUBSCRIBE},
 *       {@code UNSUBSCRIBE}, heartbeats and {@code DISCONNECT}: every action goes through REST, which
 *       has the error bodies, the audit and the locks.
 * </ul>
 *
 * <p>Message security is composed by hand, without {@code @EnableWebSocketSecurity}, whose mandatory
 * CSRF token on {@code CONNECT} has no meaning for a bearer token: the JWT interceptor, then Spring
 * Security's {@link SecurityContextChannelInterceptor} and {@link AuthorizationChannelInterceptor},
 * in that order.
 */
@Configuration
@EnableWebSocketMessageBroker
public class KitchenDisplayWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final String ENDPOINT = "/ws/kitchen";

    private static final long HEARTBEAT_MILLISECONDS = 10_000;

    private final AccessTokenAuthenticator accessTokenAuthenticator;
    private final CorsProperties corsProperties;
    private final TaskScheduler heartbeatScheduler;

    public KitchenDisplayWebSocketConfig(
            AccessTokenAuthenticator accessTokenAuthenticator,
            CorsProperties corsProperties,
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler heartbeatScheduler) {
        this.accessTokenAuthenticator = accessTokenAuthenticator;
        this.corsProperties = corsProperties;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setErrorHandler(new StompErrorCodeHandler());
        registry.addEndpoint(ENDPOINT)
                .setAllowedOriginPatterns(corsProperties.allowedOrigins().toArray(String[]::new))
                .addInterceptors(new QueryStringRefusingHandshakeInterceptor());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] {HEARTBEAT_MILLISECONDS, HEARTBEAT_MILLISECONDS})
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(
                new StompConnectAuthenticationInterceptor(accessTokenAuthenticator),
                new SecurityContextChannelInterceptor(),
                new AuthorizationChannelInterceptor(messageAuthorization()));
    }

    /**
     * Who may do what on the session: the kitchen topics for {@code KITCHEN} and {@code ADMIN} (K1,
     * K8), the ready items for {@code WAITER} and {@code ADMIN} (K10), and nothing else.
     * {@code DISCONNECT} is free, so a session that never authenticated still closes quietly.
     */
    private static AuthorizationManager<Message<?>> messageAuthorization() {
        return MessageMatcherDelegatingAuthorizationManager.builder()
                .simpTypeMatchers(SimpMessageType.DISCONNECT).permitAll()
                .simpTypeMatchers(SimpMessageType.CONNECT, SimpMessageType.HEARTBEAT, SimpMessageType.UNSUBSCRIBE)
                        .authenticated()
                .simpSubscribeDestMatchers("/topic/kitchen/*").hasAnyRole("KITCHEN", "ADMIN")
                .simpSubscribeDestMatchers("/topic/restaurant/ready-items").hasAnyRole("WAITER", "ADMIN")
                .anyMessage().denyAll()
                .build();
    }
}
