package br.com.castel.app.tab;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.app.support.AbstractIntegrationTest;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import br.com.castel.identity.infra.JwtTokenIssuer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Task 2.2 end to end: tabs opened, ordered on and cancelled through the routes, persisted in the V7
 * tables and read back.
 *
 * <p>The rules of the aggregate — prices, the order of the checks, what each status accepts — are
 * covered without a database in the unit tests of {@code restaurant}. What this proves is the wiring,
 * the roles, and the two places where the database is the rule: the partial unique indexes that keep
 * one active tab per table and per card, and the {@code FOR KEY SHARE} that lets two waiters order on
 * the same tab at once. Those run with real concurrent requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TabHttpIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "tab-test-password";
    private static final String TABS = "/api/restaurant/tabs";
    private static final int CONCURRENT_OPENINGS = 5;

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    private String adminToken;
    private String waiterToken;
    private String suffix;

    @BeforeEach
    void authenticate() {
        adminToken = accessTokenFor(createUser(Role.ADMIN));
        waiterToken = accessTokenFor(createUser(Role.WAITER));
        suffix = UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void shouldOpenOrderReadCancelAndListTabsWithFrozenPrices() {
        String categoryId = createCategory();
        JsonNode pizza = createItem(categoryId, "Pizza " + suffix, "PIZZA", false, "40.00");
        String pizzaId = pizza.get("id").asString();
        send(post("/api/restaurant/menu-items/" + pizzaId + "/variants", adminToken,
                "{\"name\":\"P\",\"price\":\"35.00\"}"), 201);
        JsonNode withLarge = send(post("/api/restaurant/menu-items/" + pizzaId + "/variants", adminToken,
                "{\"name\":\"G\",\"price\":\"45.00\"}"), 201);
        String largeId = elementNamed(withLarge.get("variants"), "G").get("id").asString();
        String stuffedCrustId = createModifier("Borda " + suffix, "8.50");
        String noCostId = createModifier("Ponto " + suffix, "0.00");
        send(put("/api/restaurant/menu-items/" + pizzaId + "/modifiers/" + stuffedCrustId, adminToken,
                "{\"maxQuantity\":2}"), 200);
        send(put("/api/restaurant/menu-items/" + pizzaId + "/modifiers/" + noCostId, adminToken,
                "{\"maxQuantity\":1}"), 200);
        String buffetId = createItem(categoryId, "Buffet " + suffix, "KITCHEN", true, "59.90").get("id").asString();
        String diningTableId = createDiningTable("Mesa " + suffix);
        int cardNumber = 501;

        String tableTabId = openOnTable(diningTableId, waiterToken).get("id").asString();
        JsonNode tableTab = send(post(TABS + "/" + tableTabId + "/items", waiterToken, """
                {"menuItemId":"%s","variantId":"%s","quantity":3,
                 "modifiers":[{"modifierId":"%s","quantity":2},{"modifierId":"%s","quantity":1}],
                 "specialInstructions":"  sem cebola  "}
                """.formatted(pizzaId, largeId, stuffedCrustId, noCostId)), 201);

        JsonNode pizzaLine = tableTab.get("items").get(0);
        assertThat(tableTab.get("diningTableLabel").asString()).isEqualTo("Mesa " + suffix);
        assertThat(pizzaLine.get("variantName").asString()).isEqualTo("G");
        assertThat(pizzaLine.get("unitPrice").asString()).isEqualTo("45.00");
        assertThat(pizzaLine.get("lineTotal").asString()).isEqualTo("186.00");
        assertThat(pizzaLine.get("serviceChargeable").asBoolean()).isTrue();
        assertThat(pizzaLine.get("specialInstructions").asString()).isEqualTo("sem cebola");
        assertThat(pizzaLine.get("status").asString()).isEqualTo("PENDING");
        assertThat(pizzaLine.get("modifiers")).hasSize(2);
        assertThat(tableTab.get("subtotal").asString()).isEqualTo("186.00");

        send(patch("/api/restaurant/menu-items/" + pizzaId + "/variants/" + largeId, adminToken,
                "{\"price\":\"99.00\"}"), 200);

        String cardTabId = openOnCard(cardNumber, waiterToken).get("id").asString();
        JsonNode cardTab = send(post(TABS + "/" + cardTabId + "/items", waiterToken,
                "{\"menuItemId\":\"%s\",\"weightGrams\":437}".formatted(buffetId)), 201);
        JsonNode plate = cardTab.get("items").get(0);
        assertThat(plate.get("weightGrams").asInt()).isEqualTo(437);
        assertThat(plate.get("pricePerKilo").asString()).isEqualTo("59.90");
        assertThat(plate.get("lineTotal").asString()).isEqualTo("26.18");
        assertThat(plate.get("serviceChargeable").asBoolean()).isFalse();
        assertThat(plate.get("status").asString()).isEqualTo("DELIVERED");

        JsonNode read = send(get(TABS + "/" + tableTabId, waiterToken), 200);
        assertThat(read.get("items").get(0).get("lineTotal").asString()).isEqualTo("186.00");
        assertThat(read.get("items").get(0).get("unitPrice").asString()).isEqualTo("45.00");

        JsonNode afterCancellation = send(post(TABS + "/" + cardTabId + "/items/" + plate.get("id").asString()
                + "/cancel", waiterToken, "{\"reason\":\"  pesado errado  \"}"), 200);
        JsonNode cancelledPlate = afterCancellation.get("items").get(0);
        assertThat(cancelledPlate.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(cancelledPlate.get("cancellationReason").asString()).isEqualTo("pesado errado");
        assertThat(cancelledPlate.get("cancelledBy").isNull()).isFalse();
        assertThat(afterCancellation.get("subtotal").asString()).isEqualTo("0.00");

        JsonNode byTable = send(get(TABS + "?diningTableId=" + diningTableId, waiterToken), 200);
        assertThat(byTable).hasSize(1);
        assertThat(byTable.get(0).get("subtotal").asString()).isEqualTo("186.00");
        assertThat(byTable.get(0).get("activeItemCount").asInt()).isEqualTo(1);
        JsonNode byCard = send(get(TABS + "?cardNumber=" + cardNumber, waiterToken), 200);
        assertThat(ids(byCard)).containsExactly(cardTabId);
        assertThat(byCard.get(0).get("activeItemCount").asInt()).isZero();

        JsonNode cancelledTab = send(post(TABS + "/" + cardTabId + "/cancel", waiterToken,
                "{\"reason\":\"cliente desistiu\"}"), 200);
        assertThat(cancelledTab.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(send(get(TABS + "?cardNumber=" + cardNumber, waiterToken), 200)).isEmpty();
        send(post(TABS + "/" + tableTabId + "/cancel", waiterToken, "{\"reason\":\"engano\"}"), 409);
    }

    @Test
    void shouldRefuseTheKitchenAndTheDeactivationOfATableWithAnOpenTab() {
        String kitchenToken = accessTokenFor(createUser(Role.KITCHEN));
        String diningTableId = createDiningTable("Varanda " + suffix);
        openOnTable(diningTableId, waiterToken);

        assertThat(send(get(TABS, kitchenToken), 403).get("code").asString()).isEqualTo("ACCESS_DENIED");
        assertThat(send(post("/api/restaurant/dining-tables/" + diningTableId + "/deactivate", adminToken, ""), 409)
                        .get("code").asString())
                .isEqualTo("DINING_TABLE_HAS_OPEN_TAB");
    }

    @Test
    void shouldRecordBothItemsWhenTwoWaitersOrderOnTheSameTabAtOnce() throws Exception {
        String sodaId = createItem(createCategory(), "Refrigerante " + suffix, "BAR", false, "6.00")
                .get("id").asString();
        String tabId = openOnTable(createDiningTable("Mesa dupla " + suffix), waiterToken).get("id").asString();
        String secondWaiterToken = accessTokenFor(createUser(Role.WAITER));
        String order = "{\"menuItemId\":\"%s\"}".formatted(sodaId);

        List<Integer> statuses = concurrently(List.of(
                () -> status(post(TABS + "/" + tabId + "/items", waiterToken, order)),
                () -> status(post(TABS + "/" + tabId + "/items", secondWaiterToken, order))));

        assertThat(statuses).containsExactly(201, 201);
        JsonNode tab = send(get(TABS + "/" + tabId, waiterToken), 200);
        assertThat(tab.get("items")).hasSize(2);
        assertThat(tab.get("subtotal").asString()).isEqualTo("12.00");
    }

    @Test
    void shouldOpenOnlyOneTabWhenFiveRequestsOpenTheSameTableAtOnce() throws Exception {
        String diningTableId = createDiningTable("Corrida " + suffix);
        String body = "{\"origin\":\"TABLE_SERVICE\",\"diningTableId\":\"%s\"}".formatted(diningTableId);

        List<HttpResponse<String>> responses = openConcurrently(body);

        assertOneOpenedAndTheRestRefusedWith(responses, "TAB_ALREADY_OPEN_FOR_DINING_TABLE");
        assertThat(send(get(TABS + "?diningTableId=" + diningTableId, waiterToken), 200)).hasSize(1);
    }

    @Test
    void shouldOpenOnlyOneTabWhenFiveRequestsOpenTheSameCardAtOnce() throws Exception {
        int cardNumber = 777;
        String body = "{\"origin\":\"SELF_SERVICE\",\"cardNumber\":%d}".formatted(cardNumber);

        List<HttpResponse<String>> responses = openConcurrently(body);

        assertOneOpenedAndTheRestRefusedWith(responses, "TAB_ALREADY_OPEN_FOR_CARD");
        assertThat(send(get(TABS + "?cardNumber=" + cardNumber, waiterToken), 200)).hasSize(1);
    }

    // ------------------------------------------------------------- concurrency

    private List<HttpResponse<String>> openConcurrently(String body) throws Exception {
        List<Callable<HttpResponse<String>>> openings = new ArrayList<>();
        for (int attempt = 0; attempt < CONCURRENT_OPENINGS; attempt++) {
            openings.add(() -> exchange(post(TABS, waiterToken, body)));
        }
        return concurrently(openings);
    }

    private void assertOneOpenedAndTheRestRefusedWith(List<HttpResponse<String>> responses, String code) {
        assertThat(responses).filteredOn(response -> response.statusCode() == 201).hasSize(1);
        List<HttpResponse<String>> refused =
                responses.stream().filter(response -> response.statusCode() != 201).toList();
        assertThat(refused).hasSize(CONCURRENT_OPENINGS - 1);
        for (HttpResponse<String> response : refused) {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
            assertThat(jsonMapper.readTree(response.body()).get("code").asString()).isEqualTo(code);
        }
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

    // ------------------------------------------------------------- fixtures through the routes

    private String createCategory() {
        return send(post("/api/restaurant/menu-categories", adminToken,
                        "{\"name\":\"Tab %s %s\",\"displayOrder\":1}".formatted(suffix, UUID.randomUUID())), 201)
                .get("id").asString();
    }

    private JsonNode createItem(String categoryId, String name, String prepStation, boolean soldByWeight, String price) {
        return send(post("/api/restaurant/menu-items", adminToken, """
                {"categoryId":"%s","name":"%s","prepStation":"%s","soldByWeight":%s,"price":"%s"}
                """.formatted(categoryId, name, prepStation, soldByWeight, price)), 201);
    }

    private String createModifier(String name, String price) {
        return send(post("/api/restaurant/modifiers", adminToken,
                        "{\"name\":\"%s\",\"price\":\"%s\"}".formatted(name, price)), 201)
                .get("id").asString();
    }

    private String createDiningTable(String label) {
        String shortLabel = label.length() > 20 ? label.substring(0, 20) : label;
        return send(post("/api/restaurant/dining-tables", adminToken, "{\"label\":\"%s\"}".formatted(shortLabel)), 201)
                .get("id").asString();
    }

    private JsonNode openOnTable(String diningTableId, String token) {
        return send(post(TABS, token,
                "{\"origin\":\"TABLE_SERVICE\",\"diningTableId\":\"%s\"}".formatted(diningTableId)), 201);
    }

    private JsonNode openOnCard(int cardNumber, String token) {
        return send(post(TABS, token, "{\"origin\":\"SELF_SERVICE\",\"cardNumber\":%d}".formatted(cardNumber)), 201);
    }

    // ------------------------------------------------------------- reading the answers

    private static JsonNode elementNamed(JsonNode array, String name) {
        return StreamSupport.stream(array.spliterator(), false)
                .filter(element -> element.get("name").asString().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No element named " + name + " in " + array));
    }

    private static List<String> ids(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(element -> element.get("id").asString()).toList();
    }

    // ------------------------------------------------------------- http helpers

    private HttpRequest.Builder get(String path, String token) {
        return authorized(path, token).GET();
    }

    private HttpRequest.Builder post(String path, String token, String body) {
        return authorized(path, token).POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder put(String path, String token, String body) {
        return authorized(path, token).PUT(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder patch(String path, String token, String body) {
        return authorized(path, token).method("PATCH", HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder authorized(String path, String token) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token);
    }

    private int status(HttpRequest.Builder builder) {
        return exchange(builder).statusCode();
    }

    private JsonNode send(HttpRequest.Builder builder, int expectedStatus) {
        HttpResponse<String> response = exchange(builder);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return jsonMapper.readTree(response.body());
    }

    private HttpResponse<String> exchange(HttpRequest.Builder builder) {
        try {
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    // ------------------------------------------------------------- identity fixtures

    private String createUser(Role role) {
        String username = "tabs_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(User.create(PROPERTY_ID, username, "Tab Test User", PASSWORD, Set.of(role), passwordEncoder));
        return username;
    }

    private String accessTokenFor(String username) {
        return new JwtTokenIssuer(jwtSecret, clock)
                .issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }
}
