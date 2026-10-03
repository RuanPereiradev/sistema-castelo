package br.com.castel.app.kitchen;

import static br.com.castel.app.kitchen.StompTestClient.MESSAGE_TIMEOUT_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import tools.jackson.databind.JsonNode;

/**
 * Task 3.5 over a real STOMP session: the CONNECT is authenticated by the access token and refused
 * with its code, each topic is open only to its roles, nothing is accepted from the client, and a
 * change reaches only the topic of its station, only after it is committed.
 *
 * <p>A topic that must stay silent is checked with a short wait after a message that did arrive on
 * another topic of the same session: the broker keeps the publish order (preservePublishOrder), so
 * anything wrongly sent earlier would already be there.
 *
 * <p>Task 3.6 adds the {@code TRANSFERRED} message, pushed when an item changes tab.
 */
class KitchenDisplayWebSocketIntegrationTest extends AbstractKitchenDisplayIntegrationTest {

    private static final long SILENCE_MILLISECONDS = 500;

    @Autowired
    private SimpMessageSendingOperations messaging;

    private StompTestClient client;

    @BeforeEach
    void createClient() {
        client = new StompTestClient(port, messaging);
    }

    @AfterEach
    void stopClient() {
        client.close();
    }

    @Test
    void shouldAnswerTheCodeWhenTheConnectCarriesNoTokenOrAnExpiredOne() throws Exception {
        assertThat(client.errorOnConnect(null)).isEqualTo("AUTHENTICATION_REQUIRED");

        Clock anHourAgo = Clock.offset(clock, Duration.ofHours(-1));
        assertThat(client.errorOnConnect(accessTokenFor(newWaiterUsername(), anHourAgo))).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void shouldRefuseAHandshakeCarryingAQueryString() {
        Throwable refusal = catchThrowable(() -> HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(client.url() + "?access_token=" + kitchenToken), new WebSocket.Listener() {})
                .get(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertThat(refusal).hasCauseInstanceOf(WebSocketHandshakeException.class);
        assertThat(((WebSocketHandshakeException) refusal.getCause()).getResponse().statusCode()).isEqualTo(400);
    }

    /** Raw frames, because the STOMP client never sends anything before its CONNECT. */
    @Test
    void shouldAnswerAuthenticationRequiredToAFrameBeforeTheConnect() throws Exception {
        CompletableFuture<String> firstFrame = new CompletableFuture<>();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(client.url()), new WebSocket.Listener() {
                    private final StringBuilder frame = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        frame.append(data);
                        if (last) {
                            firstFrame.complete(frame.toString());
                        }
                        webSocket.request(1);
                        return null;
                    }
                })
                .get(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        socket.sendText("SUBSCRIBE\nid:0\ndestination:/topic/kitchen/PIZZA\n\n\0", true);

        assertThat(firstFrame.get(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .startsWith("ERROR")
                .contains("message:AUTHENTICATION_REQUIRED");
    }

    @Test
    void shouldRefuseAWaiterSubscribingToTheKitchen() throws Exception {
        StompTestClient.Session waiter = client.connect(waiterToken);

        waiter.subscribeExpectingRefusal("/topic/kitchen/PIZZA");

        assertThat(waiter.nextError()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void shouldRefuseTheKitchenSubscribingToTheReadyItemsOfTheWaiters() throws Exception {
        StompTestClient.Session kitchen = client.connect(kitchenToken);

        kitchen.subscribeExpectingRefusal("/topic/restaurant/ready-items");

        assertThat(kitchen.nextError()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void shouldRefuseAnyMessageSentByTheClient() throws Exception {
        StompTestClient.Session kitchen = client.connect(kitchenToken);

        kitchen.stomp.send("/topic/kitchen/PIZZA", "{}".getBytes());

        assertThat(kitchen.nextError()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void shouldPushOnlyCommittedChangesAndOnlyToTheStationOfTheItem() throws Exception {
        String categoryId = createCategory();
        String pizzaId = createItem(categoryId, "Pizza", "PIZZA", false);
        String soldOutId = createItem(categoryId, "Esgotada", "PIZZA", false);
        send(post("/api/restaurant/menu-items/" + soldOutId + "/unavailable", adminToken, ""), 200);
        String tabId = openTabOnNewTable("Tela");
        StompTestClient.Session kitchen = client.connect(kitchenToken);
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

    /**
     * Task 3.6: an item that changed tab is pushed again, to the topic of its own station, carrying
     * the table it is on now. Without the message the dish would sit on the screen under the table
     * it left, and the kitchen would walk it to the wrong place.
     */
    @Test
    void shouldPushTheTransferredItemToItsStationWithTheTableItArrivedOn() throws Exception {
        String categoryId = createCategory();
        String pizzaId = createItem(categoryId, "Pizza", "PIZZA", false);
        String source = openTabOnNewTable("Sai");
        String destination = openTabOnNewTable("Chega");
        StompTestClient.Session kitchen = client.connect(kitchenToken);
        BlockingQueue<JsonNode> pizzaTopic = kitchen.subscribe("/topic/kitchen/PIZZA");
        BlockingQueue<JsonNode> barTopic = kitchen.subscribe("/topic/kitchen/BAR");
        String pizza = order(source, pizzaId);
        assertThat(pizzaTopic.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();

        send(post(TABS + "/" + source + "/transfer", waiterToken,
                "{\"toTabId\":\"%s\",\"itemIds\":[\"%s\"]}".formatted(destination, pizza)), 200);

        JsonNode transferred = pizzaTopic.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(transferred).isNotNull();
        assertThat(transferred.get("type").asString()).isEqualTo("TRANSFERRED");
        assertThat(transferred.get("item").get("itemId").asString()).isEqualTo(pizza);
        assertThat(transferred.get("item").get("tabId").asString()).isEqualTo(destination);
        assertThat(transferred.get("item").get("diningTableLabel").asString()).isEqualTo("Chega " + suffix);
        assertThat(transferred.get("item").get("status").asString()).isEqualTo("PENDING");
        assertThat(barTopic.poll(SILENCE_MILLISECONDS, TimeUnit.MILLISECONDS))
                .as("only the station of the item hears about it")
                .isNull();
    }
}
