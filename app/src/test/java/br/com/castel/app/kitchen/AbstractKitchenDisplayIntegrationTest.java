package br.com.castel.app.kitchen;

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
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the kitchen display tests share: a real server, users of each role with their tokens, and the
 * menu, tables and tabs created through the routes. The delay limits of the V10 are seeded for the
 * test property by {@link AbstractIntegrationTest}, with the other settings of the migrations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractKitchenDisplayIntegrationTest extends AbstractIntegrationTest {

    static final String TABS = "/api/restaurant/tabs";
    static final String KITCHEN = "/api/kitchen";

    private static final String PASSWORD = "kitchen-test-password";

    @LocalServerPort
    int port;

    final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    Clock clock;

    @Value("${app.security.jwt.secret}")
    String jwtSecret;

    String adminToken;
    String waiterToken;
    String kitchenToken;
    String suffix;

    @BeforeEach
    void prepareUsers() {
        adminToken = accessTokenFor(createUser(Role.ADMIN));
        waiterToken = accessTokenFor(createUser(Role.WAITER));
        kitchenToken = accessTokenFor(createUser(Role.KITCHEN));
        suffix = UUID.randomUUID().toString().substring(0, 8);
    }

    // ------------------------------------------------------------- fixtures through the routes

    String createCategory() {
        return send(post("/api/restaurant/menu-categories", adminToken,
                        "{\"name\":\"KDS %s %s\",\"displayOrder\":1}".formatted(suffix, UUID.randomUUID())), 201)
                .get("id").asString();
    }

    String createItem(String categoryId, String name, String prepStation, boolean soldByWeight) {
        return send(post("/api/restaurant/menu-items", adminToken, """
                {"categoryId":"%s","name":"%s %s","prepStation":"%s","soldByWeight":%s,"price":"10.00"}
                """.formatted(categoryId, name, suffix, prepStation, soldByWeight)), 201).get("id").asString();
    }

    String openTabOnNewTable(String label) {
        String diningTableId = send(post("/api/restaurant/dining-tables", adminToken,
                        "{\"label\":\"%s %s\"}".formatted(label, suffix)), 201)
                .get("id").asString();
        return send(post(TABS, waiterToken,
                        "{\"origin\":\"TABLE_SERVICE\",\"diningTableId\":\"%s\"}".formatted(diningTableId)), 201)
                .get("id").asString();
    }

    /** Orders one unit of the menu item and answers the id of the new tab item. */
    String order(String tabId, String menuItemId) {
        JsonNode items = send(post(TABS + "/" + tabId + "/items", waiterToken,
                        "{\"menuItemId\":\"%s\"}".formatted(menuItemId)), 201)
                .get("items");
        return items.get(items.size() - 1).get("id").asString();
    }

    static boolean containsItem(JsonNode queue, String itemId) {
        return StreamSupport.stream(queue.get("items").spliterator(), false)
                .anyMatch(ticket -> ticket.get("itemId").asString().equals(itemId));
    }

    // ------------------------------------------------------------- http helpers

    HttpRequest.Builder get(String path, String token) {
        return authorized(path, token).GET();
    }

    HttpRequest.Builder post(String path, String token, String body) {
        return authorized(path, token).POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder authorized(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    JsonNode send(HttpRequest.Builder builder, int expectedStatus) {
        HttpResponse<String> response = exchange(builder);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return jsonMapper.readTree(response.body());
    }

    String codeOf(HttpRequest.Builder builder, int expectedStatus) {
        return send(builder, expectedStatus).get("code").asString();
    }

    HttpResponse<String> exchange(HttpRequest.Builder builder) {
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
        String username = "kds_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(User.create(PROPERTY_ID, username, "KDS Test User", PASSWORD, Set.of(role), passwordEncoder));
        return username;
    }

    String accessTokenFor(String username) {
        return accessTokenFor(username, clock);
    }

    String accessTokenFor(String username, Clock issuedBy) {
        return new JwtTokenIssuer(jwtSecret, issuedBy)
                .issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }

    String newWaiterUsername() {
        return createUser(Role.WAITER);
    }
}
