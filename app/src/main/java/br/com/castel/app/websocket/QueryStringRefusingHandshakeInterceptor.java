package br.com.castel.app.websocket;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Refuses a handshake whose URL carries a query string, with 400. The endpoint takes nothing from the
 * URL, and a token put there would end up in the logs of every proxy on the way: the token travels
 * only in the {@code CONNECT} frame.
 */
class QueryStringRefusingHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler webSocketHandler,
            Map<String, Object> attributes) {
        if (request.getURI().getRawQuery() == null) {
            return true;
        }
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler webSocketHandler,
            @Nullable Exception exception) {
        // nothing to do after an accepted handshake
    }
}
