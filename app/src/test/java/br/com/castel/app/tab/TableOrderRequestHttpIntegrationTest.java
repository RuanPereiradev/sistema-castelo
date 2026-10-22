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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Task qr-table-ordering end to end: the customer orders from the QR of the table without login, the
 * waiter confirms or rejects, and only a confirmed request reaches the tab and the kitchen.
 *
 * <p>The transitions and the 30-minute window are covered without a database in
 * {@code TableOrderRequestTest}. What this proves is the wiring, the roles, the one-pending-per-table
 * index and the codes each route answers, one case per rule.
 *
 * <p>The expiry job is pushed a day away in this context, so a request moved back in time by SQL is
 * still stored {@code PENDING} when the waiter answers it, which is the lazy expiry of decision #29.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "castel.restaurant.table-order-request-expiry-period=PT24H")
class TableOrderRequestHttpIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "qr-test-password";
    private static final String TABS = "/api/restaurant/tabs";
    private static final String REQUESTS = "/api/restaurant/table-order-requests";
    private static final String CUSTOMER_NAME = "Ana";
    private static final String DEVICE_ID = "phone-of-ana-0001";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    private String adminToken;
    private String waiterToken;
    private String suffix;
    private String sodaId;

    @BeforeEach
    void arrange() {
        adminToken = accessTokenFor(createUser(Role.ADMIN));
        waiterToken = accessTokenFor(createUser(Role.WAITER));
        suffix = UUID.randomUUID().toString().substring(0, 8);
        sodaId = createItem("Soda " + suffix, false, "6.00");
    }

    @Test
    void shouldOpenTheTabAndReachTheKitchenWhenTheWaiterConfirmsARequestSentWithoutLogin() {
        JsonNode table = createDiningTable("QR " + suffix);
        String orderingToken = table.get("orderingToken").asString();

        JsonNode submitted = send(publicPost(orderingToken, orderOf(sodaId, 2)), 201);
        String requestId = submitted.get("id").asString();
        assertThat(submitted.get("status").asString()).isEqualTo("PENDING");
        assertThat(send(get(TABS + "?diningTableId=" + table.get("id").asString(), waiterToken), 200))
                .as("nothing reaches a tab before the waiter confirms")
                .isEmpty();

        JsonNode queue = send(get(REQUESTS, waiterToken), 200);
        JsonNode listed = elementWithId(queue, requestId);
        assertThat(listed.get("diningTableLabel").asString()).isEqualTo("QR " + suffix);
        assertThat(listed.get("lines").get(0).get("menuItemName").asString()).isEqualTo("Soda " + suffix);

        JsonNode confirmation = send(post(REQUESTS + "/" + requestId + "/confirm", waiterToken, ""), 200);
        String tabId = confirmation.get("tabId").asString();
        assertThat(confirmation.get("request").get("status").asString()).isEqualTo("CONFIRMED");

        JsonNode tab = send(get(TABS + "/" + tabId, waiterToken), 200);
        assertThat(tab.get("status").asString()).isEqualTo("OPEN");
        assertThat(tab.get("items")).hasSize(1);
        assertThat(tab.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
        String itemId = tab.get("items").get(0).get("id").asString();
        JsonNode kitchen = send(get("/api/kitchen/queue?station=BAR", accessTokenFor(createUser(Role.KITCHEN))), 200);
        assertThat(StreamSupport.stream(kitchen.get("items").spliterator(), false)
                        .map(ticket -> ticket.get("itemId").asString()))
                .contains(itemId);
        assertThat(send(publicGet(orderingToken, "/order-requests/" + requestId), 200).get("status").asString())
                .isEqualTo("CONFIRMED");
    }

    @Test
    void shouldAppendToTheTabAlreadyOpenOnTheTable() {
        JsonNode table = createDiningTable("Aberta " + suffix);
        String openTabId = openOnTable(table.get("id").asString());
        send(post(TABS + "/" + openTabId + "/items", waiterToken, "{\"menuItemId\":\"%s\"}".formatted(sodaId)), 201);
        String requestId = submit(table, orderOf(sodaId, 1));

        JsonNode confirmation = send(post(REQUESTS + "/" + requestId + "/confirm", waiterToken, ""), 200);

        assertThat(confirmation.get("tabId").asString()).isEqualTo(openTabId);
        assertThat(send(get(TABS + "/" + openTabId, waiterToken), 200).get("items")).hasSize(2);
    }

    @Test
    void shouldRejectWithoutOpeningAnything() {
        JsonNode table = createDiningTable("Trote " + suffix);
        String requestId = submit(table, orderOf(sodaId, 1));

        JsonNode rejected = send(post(REQUESTS + "/" + requestId + "/reject", waiterToken, ""), 200);

        assertThat(rejected.get("status").asString()).isEqualTo("REJECTED");
        assertThat(send(get(TABS + "?diningTableId=" + table.get("id").asString(), waiterToken), 200)).isEmpty();
        assertThat(ids(send(get(REQUESTS, waiterToken), 200))).doesNotContain(requestId);
    }

    @Test
    void shouldRefuseASecondPendingRequestOnTheSameTable() {
        JsonNode table = createDiningTable("Dupla " + suffix);
        submit(table, orderOf(sodaId, 1));

        assertCode(publicPost(table.get("orderingToken").asString(), orderOf(sodaId, 1)), 409,
                "TABLE_ORDER_REQUEST_ALREADY_PENDING");
    }

    @Test
    void shouldRefuseARequestWhileTheTabOfTheTableIsClosing() {
        JsonNode table = createDiningTable("Fecha " + suffix);
        String tabId = openOnTable(table.get("id").asString());
        send(post(TABS + "/" + tabId + "/items", waiterToken, "{\"menuItemId\":\"%s\"}".formatted(sodaId)), 201);
        send(post(TABS + "/" + tabId + "/closing", waiterToken, ""), 200);

        assertCode(publicPost(table.get("orderingToken").asString(), orderOf(sodaId, 1)), 409,
                "TAB_CLOSING_REFUSES_ORDER_REQUEST");
    }

    @Test
    void shouldAnswerNotFoundForAnUnknownOrderingToken() {
        assertCode(publicPost(UUID.randomUUID().toString(), orderOf(sodaId, 1)), 404, "DINING_TABLE_NOT_FOUND");
    }

    @Test
    void shouldRefuseAnItemSoldByWeightAtSubmission() {
        JsonNode table = createDiningTable("Peso " + suffix);
        String buffetId = createItem("Buffet " + suffix, true, "59.90");

        assertCode(publicPost(table.get("orderingToken").asString(), orderOf(buffetId, 1)), 422,
                "INVALID_TABLE_ORDER_REQUEST");
    }

    @Test
    void shouldKeepTheRequestPendingWhenTheItemRanOutBeforeConfirmation() {
        JsonNode table = createDiningTable("Esgota " + suffix);
        String requestId = submit(table, orderOf(sodaId, 1));
        send(post("/api/restaurant/menu-items/" + sodaId + "/unavailable", adminToken, ""), 200);

        assertCode(post(REQUESTS + "/" + requestId + "/confirm", waiterToken, ""), 422, "MENU_ITEM_UNAVAILABLE");

        assertThat(statusSeenByThePhone(table, requestId)).isEqualTo("PENDING");
        assertThat(send(get(TABS + "?diningTableId=" + table.get("id").asString(), waiterToken), 200)).isEmpty();
    }

    @Test
    void shouldRefuseConfirmationOfAnExpiredRequest() {
        JsonNode table = createDiningTable("Vence " + suffix);
        String requestId = submit(table, orderOf(sodaId, 1));
        moveBackThirtyOneMinutes(requestId);

        assertCode(post(REQUESTS + "/" + requestId + "/confirm", waiterToken, ""), 409, "TABLE_ORDER_REQUEST_EXPIRED");

        assertThat(statusSeenByThePhone(table, requestId)).isEqualTo("EXPIRED");
    }

    @Test
    void shouldRefuseTheSecondConfirmationOfTheSameRequest() {
        JsonNode table = createDiningTable("Duas " + suffix);
        String requestId = submit(table, orderOf(sodaId, 1));
        String tabId = send(post(REQUESTS + "/" + requestId + "/confirm", waiterToken, ""), 200)
                .get("tabId").asString();

        assertCode(post(REQUESTS + "/" + requestId + "/confirm", accessTokenFor(createUser(Role.WAITER)), ""), 409,
                "TABLE_ORDER_REQUEST_NOT_PENDING");

        assertThat(send(get(TABS + "/" + tabId, waiterToken), 200).get("items")).hasSize(1);
    }

    @Test
    void shouldLetTheFrontDeskReadTabsButNotOpenThem() {
        String frontDeskToken = accessTokenFor(createUser(Role.FRONT_DESK));
        JsonNode table = createDiningTable("Recep " + suffix);
        String tabId = openOnTable(table.get("id").asString());

        send(get(TABS, frontDeskToken), 200);
        send(get(TABS + "/" + tabId, frontDeskToken), 200);
        assertCode(post(TABS, frontDeskToken, openingBody(createDiningTable("Recep2 " + suffix).get("id").asString())),
                403, "ACCESS_DENIED");
    }

    @Test
    void shouldStopAnsweringTheOldQrOnceTheOrderingTokenIsRegenerated() {
        JsonNode table = createDiningTable("Novo QR " + suffix);
        String oldToken = table.get("orderingToken").asString();

        JsonNode regenerated = send(post("/api/restaurant/dining-tables/" + table.get("id").asString()
                + "/ordering-token", adminToken, ""), 200);

        String newToken = regenerated.get("orderingToken").asString();
        assertThat(newToken).isNotEqualTo(oldToken);
        assertCode(publicGet(oldToken, ""), 404, "DINING_TABLE_NOT_FOUND");
        send(publicGet(newToken, ""), 200);
    }

    @Test
    void shouldNotShowTheRequestOfAnotherTableToTheCustomer() {
        JsonNode ownTable = createDiningTable("Minha " + suffix);
        JsonNode otherTable = createDiningTable("Outra " + suffix);
        String requestId = submit(ownTable, orderOf(sodaId, 1));

        assertCode(publicGet(otherTable.get("orderingToken").asString(), "/order-requests/" + requestId), 404,
                "TABLE_ORDER_REQUEST_NOT_FOUND");
    }

    @Test
    void shouldShowTheWaiterTheNameOfTheCustomerButNeverTheDeviceCode() {
        JsonNode table = createDiningTable("Nome " + suffix);
        String requestId = submit(table, orderOf(sodaId, 1));
        assertThat(elementWithId(send(get(REQUESTS, waiterToken), 200), requestId).get("customerName").asString())
                .isEqualTo(CUSTOMER_NAME);

        String tabId = send(post(REQUESTS + "/" + requestId + "/confirm", waiterToken, ""), 200)
                .get("tabId").asString();

        HttpResponse<String> tab = exchange(get(TABS + "/" + tabId, waiterToken));
        assertThat(jsonMapper.readTree(tab.body()).get("items").get(0).get("customerName").asString())
                .isEqualTo(CUSTOMER_NAME);
        assertThat(tab.body()).doesNotContain(DEVICE_ID);
    }

    @Test
    void shouldRefuseAnOrderWithoutTheNameOfTheCustomer() {
        JsonNode table = createDiningTable("Sem nome " + suffix);

        assertCode(publicPost(table.get("orderingToken").asString(), orderBy(null, DEVICE_ID, sodaId, 1)), 422,
                "INVALID_CUSTOMER_NAME");
    }

    @Test
    void shouldRefuseAnOrderWithAMalformedDeviceCode() {
        JsonNode table = createDiningTable("Sem aparelho " + suffix);

        assertCode(publicPost(table.get("orderingToken").asString(), orderBy(CUSTOMER_NAME, "short", sodaId, 1)), 422,
                "INVALID_CUSTOMER_DEVICE_ID");
    }

    // ------------------------------------------------------------- fixtures

    private String submit(JsonNode table, String body) {
        return send(publicPost(table.get("orderingToken").asString(), body), 201).get("id").asString();
    }

    private String statusSeenByThePhone(JsonNode table, String requestId) {
        return send(publicGet(table.get("orderingToken").asString(), "/order-requests/" + requestId), 200)
                .get("status").asString();
    }

    /** Ages the request past its 30-minute window, as the clock would. */
    private void moveBackThirtyOneMinutes(String requestId) {
        int updated = jdbcTemplate.update("update table_order_request "
                + "set requested_at = requested_at - interval '31 minutes', "
                + "expires_at = expires_at - interval '31 minutes' where id = ?", UUID.fromString(requestId));
        assertThat(updated).isEqualTo(1);
    }

    private static String orderOf(String menuItemId, int quantity) {
        return orderBy(CUSTOMER_NAME, DEVICE_ID, menuItemId, quantity);
    }

    private static String orderBy(String customerName, String customerDeviceId, String menuItemId, int quantity) {
        return """
                {"customerName":%s,"customerDeviceId":%s,
                 "lines":[{"menuItemId":"%s","quantity":%d,"modifiers":[]}]}
                """.formatted(quoted(customerName), quoted(customerDeviceId), menuItemId, quantity);
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String openingBody(String diningTableId) {
        return "{\"origin\":\"TABLE_SERVICE\",\"diningTableId\":\"%s\"}".formatted(diningTableId);
    }

    private String openOnTable(String diningTableId) {
        return send(post(TABS, waiterToken, openingBody(diningTableId)), 201).get("id").asString();
    }

    private String createItem(String name, boolean soldByWeight, String price) {
        String categoryId = send(post("/api/restaurant/menu-categories", adminToken,
                        "{\"name\":\"QR %s\",\"displayOrder\":1}".formatted(UUID.randomUUID())), 201)
                .get("id").asString();
        return send(post("/api/restaurant/menu-items", adminToken, """
                {"categoryId":"%s","name":"%s","prepStation":"BAR","soldByWeight":%s,"price":"%s"}
                """.formatted(categoryId, name, soldByWeight, price)), 201).get("id").asString();
    }

    private JsonNode createDiningTable(String label) {
        String shortLabel = label.length() > 20 ? label.substring(0, 20) : label;
        return send(post("/api/restaurant/dining-tables", adminToken, "{\"label\":\"%s\"}".formatted(shortLabel)), 201);
    }

    // ------------------------------------------------------------- reading the answers

    private static JsonNode elementWithId(JsonNode array, String id) {
        return StreamSupport.stream(array.spliterator(), false)
                .filter(element -> element.get("id").asString().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No element " + id + " in " + array));
    }

    private static List<String> ids(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(element -> element.get("id").asString()).toList();
    }

    private void assertCode(HttpRequest.Builder builder, int expectedStatus, String code) {
        assertThat(send(builder, expectedStatus).get("code").asString()).isEqualTo(code);
    }

    // ------------------------------------------------------------- http helpers

    private HttpRequest.Builder publicPost(String orderingToken, String body) {
        return anonymous("/public/tables/" + orderingToken + "/order-requests")
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder publicGet(String orderingToken, String path) {
        return anonymous("/public/tables/" + orderingToken + path).GET();
    }

    private HttpRequest.Builder anonymous(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
    }

    private HttpRequest.Builder get(String path, String token) {
        return authorized(path, token).GET();
    }

    private HttpRequest.Builder post(String path, String token, String body) {
        return authorized(path, token).POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder authorized(String path, String token) {
        return anonymous(path).header("Authorization", "Bearer " + token);
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
        String username = "qr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(User.create(PROPERTY_ID, username, "QR Test User", PASSWORD, Set.of(role), passwordEncoder));
        return username;
    }

    private String accessTokenFor(String username) {
        return new JwtTokenIssuer(jwtSecret, clock)
                .issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }
}
