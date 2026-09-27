package br.com.castel.app.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Task 3.5 over a real STOMP session: the CONNECT is authenticated by the access token and refused
 * with its code, a waiter cannot listen to the kitchen, and a change reaches only the topic of its
 * station, only after it is committed.
 *
 * <p>A topic that must stay silent is checked with a short wait after a message that did arrive on
 * another topic of the same session: the broker keeps the publish order (preservePublishOrder), so
 * anything wrongly sent earlier would already be there.
 */
class KitchenDisplayWebSocketIntegrationTest extends AbstractKitchenDisplayIntegrationTest {

    private static final long MESSAGE_TIMEOUT_SECONDS = 5;
    private static final long SILENCE_MILLISECONDS = 500;

    private static final String PROBE = "PROBE";
    private static final int PROBE_ATTEMPTS = 50;

    @Autowired
    private SimpMessageSendingOperations messaging;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void createClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JsonAsBytesConverter());
        stompClient.setDefaultHeartbeat(new long[] {0, 0});
    }

    @AfterEach
    void stopClient() {
        stompClient.stop();
    }

    @Test
    void shouldAnswerTheCodeWhenTheConnectCarriesNoTokenOrAnExpiredOne() throws Exception {
        assertThat(errorOnConnect(null)).isEqualTo("AUTHENTICATION_REQUIRED");

        Clock anHourAgo = Clock.offset(clock, Duration.ofHours(-1));
        assertThat(errorOnConnect(accessTokenFor(newWaiterUsername(), anHourAgo))).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void shouldRefuseAWaiterSubscribingToTheKitchen() throws Exception {
        Listener waiter = connect(waiterToken);

        waiter.session.subscribe("/topic/kitchen/PIZZA", new TopicHandler());

        assertThat(waiter.errors.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void shouldPushOnlyCommittedChangesAndOnlyToTheStationOfTheItem() throws Exception {
        String categoryId = createCategory();
        String pizzaId = createItem(categoryId, "Pizza", "PIZZA", false);
        String soldOutId = createItem(categoryId, "Esgotada", "PIZZA", false);
        send(post("/api/restaurant/menu-items/" + soldOutId + "/unavailable", adminToken, ""), 200);
        String tabId = openTabOnNewTable("Tela");
        Listener kitchen = connect(kitchenToken);
        BlockingQueue<JsonNode> pizzaTopic = kitchen.subscribe("/topic/kitchen/PIZZA");
        BlockingQueue<JsonNode> barTopic = kitchen.subscribe("/topic/kitchen/BAR");

        String pizza = order(tabId, pizzaId);

        JsonNode ordered = pizzaTopic.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(ordered).isNotNull();
        assertThat(ordered.get("type").asString()).isEqualTo("ORDERED");
        assertThat(ordered.get("item").get("itemId").asString()).isEqualTo(pizza);
        assertThat(ordered.get("item").get("status").asString()).isEqualTo("PENDING");
        assertThat(barTopic.poll(SILENCE_MILLISECONDS, TimeUnit.MILLISECONDS)).isNull();

        send(post(TABS + "/" + tabId + "/items/" + pizza + "/cancel", waiterToken, "{\"reason\":\"sem massa\"}"), 200);

        JsonNode cancelled = pizzaTopic.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(cancelled).isNotNull();
        assertThat(cancelled.get("type").asString()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("item").get("status").asString()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("cancellationReason").asString()).isEqualTo("sem massa");

        assertThat(codeOf(post(TABS + "/" + tabId + "/items", waiterToken,
                        "{\"menuItemId\":\"%s\"}".formatted(soldOutId)), 422))
                .isEqualTo("MENU_ITEM_UNAVAILABLE");
        assertThat(pizzaTopic.poll(SILENCE_MILLISECONDS, TimeUnit.MILLISECONDS)).isNull();
    }

    // ------------------------------------------------------------- stomp helpers

    /** Connects and waits for {@code CONNECTED}. */
    private Listener connect(String token) throws Exception {
        Listener listener = new Listener();
        listener.session = stompClient
                .connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders(token), listener)
                .get(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return listener;
    }

    /** Connects expecting a refusal, and answers the code of the {@code ERROR} frame. */
    private String errorOnConnect(String token) throws Exception {
        Listener listener = new Listener();
        stompClient.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders(token), listener);
        return listener.errors.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private String url() {
        return "ws://localhost:" + port + "/ws/kitchen";
    }

    private static StompHeaders connectHeaders(String token) {
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return headers;
    }

    /** One client session: the codes of its {@code ERROR} frames and the messages of its topics. */
    private final class Listener extends StompSessionHandlerAdapter {

        private final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        private StompSession session;

        /**
         * Subscribes and waits until the broker delivers to the subscription, so nothing published
         * afterwards is missed. The simple broker sends no receipt for a SUBSCRIBE, so a probe goes
         * through the broker until one arrives; probes never reach the queue answered.
         */
        BlockingQueue<JsonNode> subscribe(String destination) throws InterruptedException {
            TopicHandler handler = new TopicHandler();
            session.subscribe(destination, handler);
            for (int attempt = 0; attempt < PROBE_ATTEMPTS && handler.probed.getCount() > 0; attempt++) {
                messaging.convertAndSend(destination, (Object) Map.of("type", PROBE));
                handler.probed.await(100, TimeUnit.MILLISECONDS);
            }
            assertThat(handler.probed.getCount()).as("subscription to " + destination + " active").isZero();
            return handler.messages;
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
