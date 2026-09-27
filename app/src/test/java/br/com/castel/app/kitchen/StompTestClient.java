package br.com.castel.app.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A real STOMP client for the kitchen display tests: connects with a bearer token, collects the
 * codes of the {@code ERROR} frames and the JSON messages of each topic.
 *
 * <p>The simple broker sends no {@code RECEIPT} for a {@code SUBSCRIBE}, so {@link Session#subscribe}
 * waits for the subscription to be active by sending a probe through the broker until one arrives;
 * probes never reach the queue it answers.
 */
final class StompTestClient implements AutoCloseable {

    static final long MESSAGE_TIMEOUT_SECONDS = 5;

    private static final String PROBE = "PROBE";
    private static final int PROBE_ATTEMPTS = 50;

    private final int port;
    private final SimpMessageSendingOperations messaging;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final WebSocketStompClient stompClient;

    StompTestClient(int port, SimpMessageSendingOperations messaging) {
        this.port = port;
        this.messaging = messaging;
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JsonAsBytesConverter());
        stompClient.setDefaultHeartbeat(new long[] {0, 0});
    }

    /** Connects and waits for {@code CONNECTED}. */
    Session connect(String token) throws Exception {
        Session session = new Session();
        session.stomp = stompClient
                .connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders(token), session)
                .get(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return session;
    }

    /** Connects expecting a refusal, and answers the code of the {@code ERROR} frame. */
    String errorOnConnect(String token) throws InterruptedException {
        Session session = new Session();
        stompClient.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders(token), session);
        return session.errors.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    String url() {
        return "ws://localhost:" + port + "/ws/kitchen";
    }

    @Override
    public void close() {
        stompClient.stop();
    }

    private static StompHeaders connectHeaders(String token) {
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return headers;
    }

    /** One client session: the codes of its {@code ERROR} frames and the messages of its topics. */
    final class Session extends StompSessionHandlerAdapter {

        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession stomp;

        /** Subscribes and waits until the broker delivers to the subscription. */
        BlockingQueue<JsonNode> subscribe(String destination) throws InterruptedException {
            TopicHandler handler = new TopicHandler();
            stomp.subscribe(destination, handler);
            for (int attempt = 0; attempt < PROBE_ATTEMPTS && handler.probed.getCount() > 0; attempt++) {
                messaging.convertAndSend(destination, (Object) Map.of("type", PROBE));
                handler.probed.await(100, TimeUnit.MILLISECONDS);
            }
            assertThat(handler.probed.getCount()).as("subscription to " + destination + " active").isZero();
            return handler.messages;
        }

        /** Subscribes without waiting, for a subscription the server is expected to refuse. */
        void subscribeExpectingRefusal(String destination) {
            stomp.subscribe(destination, new TopicHandler());
        }

        String nextError() throws InterruptedException {
            return errors.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errors.add(String.valueOf(headers.getFirst("message")));
        }

        @Override
        public void handleException(
                StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
            errors.add("EXCEPTION " + exception);
        }
    }

    private final class TopicHandler implements StompFrameHandler {

        private final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        private final CountDownLatch probed = new CountDownLatch(1);

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            JsonNode message = jsonMapper.readTree((byte[]) payload);
            if (PROBE.equals(message.get("type").asString())) {
                probed.countDown();
            } else {
                messages.add(message);
            }
        }
    }

    /** The raw JSON bytes of each message, parsed by the test itself. */
    private static final class JsonAsBytesConverter extends ByteArrayMessageConverter {

        JsonAsBytesConverter() {
            addSupportedMimeTypes(MimeTypeUtils.APPLICATION_JSON);
        }
    }
}
