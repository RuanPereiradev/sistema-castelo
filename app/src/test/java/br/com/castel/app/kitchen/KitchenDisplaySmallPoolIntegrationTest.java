package br.com.castel.app.kitchen;

import static br.com.castel.app.kitchen.StompTestClient.MESSAGE_TIMEOUT_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * The push to the screens never asks the pool for a second connection. With a pool of two and more
 * simultaneous orders than that, every order answers 201 and every ticket reaches the kitchen: the
 * ticket is read inside the transaction of the order and only sent after the commit.
 *
 * <p>Its own context, because the pool size is a property of the data source.
 */
@TestPropertySource(properties = {
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.connection-timeout=2000"
})
class KitchenDisplaySmallPoolIntegrationTest extends AbstractKitchenDisplayIntegrationTest {

    private static final int SIMULTANEOUS_ORDERS = 6;

    @Autowired
    private SimpMessageSendingOperations messaging;

    @Test
    void shouldDeliverEveryOrderAndEveryMessageWhenOrdersOutnumberThePool() throws Exception {
        String pizzaId = createItem(createCategory(), "Pool", "PIZZA", false);
        String tabId = openTabOnNewTable("Pool");
        try (StompTestClient client = new StompTestClient(port, messaging)) {
            BlockingQueue<JsonNode> pizzaTopic = client.connect(kitchenToken).subscribe("/topic/kitchen/PIZZA");

            List<Integer> statuses = orderConcurrently(tabId, pizzaId);

            assertThat(statuses).containsOnly(201);
            Set<String> pushed = new HashSet<>();
            for (int message = 0; message < SIMULTANEOUS_ORDERS; message++) {
                JsonNode ordered = pizzaTopic.poll(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                assertThat(ordered).as("message %d of %d", message + 1, SIMULTANEOUS_ORDERS).isNotNull();
                pushed.add(ordered.get("item").get("itemId").asString());
            }
            assertThat(pushed).hasSize(SIMULTANEOUS_ORDERS);
        }
    }

    private List<Integer> orderConcurrently(String tabId, String menuItemId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(SIMULTANEOUS_ORDERS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int attempt = 0; attempt < SIMULTANEOUS_ORDERS; attempt++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return exchange(post(TABS + "/" + tabId + "/items", waiterToken,
                            "{\"menuItemId\":\"%s\"}".formatted(menuItemId))).statusCode();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }
}
