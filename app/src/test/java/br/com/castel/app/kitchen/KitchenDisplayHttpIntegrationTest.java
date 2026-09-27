package br.com.castel.app.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Task 3.5 through the routes: an item walks the queue of its own station and leaves it when
 * delivered, the roles stay on their side, and the kitchen and a waiter touching the same item at once
 * end in one coherent state.
 *
 * <p>The status × operation matrix lives in the unit tests of {@code restaurant}; this proves the
 * wiring, the queue read over the V10 index and the locks under real concurrent requests.
 */
class KitchenDisplayHttpIntegrationTest extends AbstractKitchenDisplayIntegrationTest {

    private static final int CONCURRENT_READY = 4;

    @Test
    void shouldWalkAnItemThroughTheQueueOfItsStationUntilTheWaiterDeliversIt() {
        String categoryId = createCategory();
        String pizzaId = createItem(categoryId, "Pizza", "PIZZA", false);
        String sodaId = createItem(categoryId, "Refri", "BAR", false);
        String buffetId = createItem(categoryId, "Buffet", "KITCHEN", true);
        String tabId = openTabOnNewTable("Mesa");
        String pizza = order(tabId, pizzaId);
        String soda = order(tabId, sodaId);
        String plate = send(post(TABS + "/" + tabId + "/items", waiterToken,
                        "{\"menuItemId\":\"%s\",\"weightGrams\":300}".formatted(buffetId)), 201)
                .get("items").get(2).get("id").asString();

        JsonNode pizzaQueue = send(get(KITCHEN + "/queue?station=PIZZA", kitchenToken), 200);
        assertThat(containsItem(pizzaQueue, pizza)).isTrue();
        assertThat(containsItem(pizzaQueue, soda)).isFalse();
        assertThat(pizzaQueue.get("warningAfterMinutes").asInt()).isEqualTo(20);
        assertThat(pizzaQueue.get("lateAfterMinutes").asInt()).isEqualTo(30);
        assertThat(containsItem(send(get(KITCHEN + "/queue?station=BAR", kitchenToken), 200), soda)).isTrue();
        assertThat(containsItem(send(get(KITCHEN + "/queue?station=KITCHEN", kitchenToken), 200), plate)).isFalse();

        JsonNode started = send(post(KITCHEN + "/items/" + pizza + "/start", kitchenToken, ""), 200);
        assertThat(started.get("status").asString()).isEqualTo("IN_PREPARATION");
        assertThat(started.get("diningTableLabel").asString()).isEqualTo("Mesa " + suffix);
        assertThat(started.get("preparationStartedAt").isNull()).isFalse();
        assertThat(codeOf(post(KITCHEN + "/items/" + pizza + "/start", kitchenToken, ""), 409))
                .isEqualTo("INVALID_TAB_ITEM_TRANSITION");
        assertThat(send(post(KITCHEN + "/items/" + pizza + "/ready", kitchenToken, ""), 200)
                        .get("status").asString())
                .isEqualTo("READY");
        JsonNode undone = send(post(KITCHEN + "/items/" + pizza + "/undo", kitchenToken, ""), 200);
        assertThat(undone.get("status").asString()).isEqualTo("IN_PREPARATION");
        assertThat(undone.get("readyAt").isNull()).isTrue();
        send(post(KITCHEN + "/items/" + pizza + "/ready", kitchenToken, ""), 200);

        JsonNode delivered = send(post(TABS + "/" + tabId + "/items/" + pizza + "/deliver", waiterToken, ""), 200)
                .get("items").get(0);
        assertThat(delivered.get("status").asString()).isEqualTo("DELIVERED");
        assertThat(delivered.get("readyAt").isNull()).isFalse();
        assertThat(delivered.get("deliveredAt").isNull()).isFalse();
        assertThat(containsItem(send(get(KITCHEN + "/queue?station=PIZZA", kitchenToken), 200), pizza)).isFalse();

        JsonNode readyAtOnce = send(post(KITCHEN + "/items/" + soda + "/ready", kitchenToken, ""), 200);
        assertThat(readyAtOnce.get("preparationStartedAt").isNull()).isTrue();
        assertThat(send(post(KITCHEN + "/items/" + soda + "/undo", kitchenToken, ""), 200).get("status").asString())
                .isEqualTo("PENDING");
    }

    @Test
    void shouldKeepEachRoleOnItsSideAndRefuseWhatTheItemCannotDo() {
        String pizzaId = createItem(createCategory(), "Calabresa", "PIZZA", false);
        String tabId = openTabOnNewTable("Papeis");
        String pizza = order(tabId, pizzaId);

        assertThat(codeOf(get(KITCHEN + "/queue?station=PIZZA", waiterToken), 403)).isEqualTo("ACCESS_DENIED");
        assertThat(codeOf(post(TABS + "/" + tabId + "/items/" + pizza + "/deliver", kitchenToken, ""), 403))
                .isEqualTo("ACCESS_DENIED");
        assertThat(codeOf(get(KITCHEN + "/queue?station=PIZZA", null), 401)).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(codeOf(get(KITCHEN + "/queue?station=GRILL", kitchenToken), 400)).isEqualTo("VALIDATION_FAILED");
        assertThat(codeOf(get(KITCHEN + "/queue", kitchenToken), 400)).isEqualTo("VALIDATION_FAILED");
        assertThat(codeOf(post(KITCHEN + "/items/" + pizza + "/undo", kitchenToken, ""), 409))
                .isEqualTo("INVALID_TAB_ITEM_TRANSITION");
        assertThat(codeOf(post(KITCHEN + "/items/0192f5a0-0000-7000-8000-000000000000/start", kitchenToken, ""), 404))
                .isEqualTo("TAB_ITEM_NOT_FOUND");

        send(post(TABS + "/" + tabId + "/items/" + pizza + "/cancel", waiterToken, "{\"reason\":\"engano\"}"), 200);
        assertThat(codeOf(post(KITCHEN + "/items/" + pizza + "/start", kitchenToken, ""), 409))
                .isEqualTo("TAB_ITEM_ALREADY_CANCELLED");
        assertThat(codeOf(post(TABS + "/" + tabId + "/items/" + pizza + "/deliver", waiterToken, ""), 409))
                .isEqualTo("TAB_ITEM_ALREADY_CANCELLED");
    }

    /**
     * One cancellation and four "ready" on the same item at once. They queue on the item's row: the
     * cancellation always lands (it is valid from any status, decision #6), at most one "ready" lands
     * before it, and every other request is refused with a code, never a 500.
     */
    @Test
    void shouldEndCancelledWithOneAuthorWhenTheKitchenMarksReadyWhileTheWaiterCancels() throws Exception {
        String tabId = openTabOnNewTable("Corrida");
        String pizza = order(tabId, createItem(createCategory(), "Marguerita", "PIZZA", false));
        List<Callable<HttpResponse<String>>> requests = new ArrayList<>();
        requests.add(() -> exchange(post(TABS + "/" + tabId + "/items/" + pizza + "/cancel", waiterToken,
                "{\"reason\":\"cliente desistiu\"}")));
        for (int attempt = 0; attempt < CONCURRENT_READY; attempt++) {
            requests.add(() -> exchange(post(KITCHEN + "/items/" + pizza + "/ready", kitchenToken, "")));
        }

        List<HttpResponse<String>> responses = concurrently(requests);

        assertThat(responses.get(0).statusCode()).as(responses.get(0).body()).isEqualTo(200);
        List<HttpResponse<String>> readies = responses.subList(1, responses.size());
        assertThat(readies).filteredOn(response -> response.statusCode() == 200).hasSizeLessThanOrEqualTo(1);
        for (HttpResponse<String> response : readies) {
            if (response.statusCode() != 200) {
                assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
                assertThat(jsonMapper.readTree(response.body()).get("code").asString())
                        .isIn("INVALID_TAB_ITEM_TRANSITION", "TAB_ITEM_ALREADY_CANCELLED");
            }
        }
        JsonNode stored = send(get(TABS + "/" + tabId, waiterToken), 200).get("items").get(0);
        assertThat(stored.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(stored.get("cancellationReason").asString()).isEqualTo("cliente desistiu");
    }

    /** Runs every task at the same moment, released together by one latch, and waits for all. */
    private static <T> List<T> concurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
