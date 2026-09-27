package br.com.castel.app.tab;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.app.support.AbstractIntegrationTest;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import br.com.castel.identity.infra.JwtTokenIssuer;
import br.com.castel.sharedkernel.Setting;
import br.com.castel.sharedkernel.SettingRepository;
import br.com.castel.sharedkernel.SettingValueType;
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
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the closing tests of task 3.2 share: a waiter and an admin, the service charge setting at
 * 10.00, and the routes to build a tab with items through HTTP.
 *
 * <p>The test database gets its property after Flyway, so the seed of V9 finds none; the setting is
 * written here through the {@link SettingRepository}, the same way the dev seeder writes it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractTabClosingIntegrationTest extends AbstractIntegrationTest {

    static final String TABS = "/api/restaurant/tabs";
    private static final String PASSWORD = "tab-closing-test-password";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SettingRepository settingRepository;

    @Autowired
    private Clock clock;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    String adminToken;
    String waiterToken;
    String suffix;

    @BeforeEach
    void prepare() {
        settingRepository.save(Setting.of("restaurant.service-charge-percent", "10.00", SettingValueType.DECIMAL));
        adminToken = accessTokenFor(createUser(Role.ADMIN));
        waiterToken = accessTokenFor(createUser(Role.WAITER));
        suffix = UUID.randomUUID().toString().substring(0, 8);
    }

    // ------------------------------------------------------------- fixtures through the routes

    String createItem(String name, String price, boolean serviceChargeEligible) {
        String categoryId = send(post("/api/restaurant/menu-categories", adminToken,
                        "{\"name\":\"Closing %s %s\",\"displayOrder\":1}".formatted(suffix, UUID.randomUUID())), 201)
                .get("id").asString();
        return send(post("/api/restaurant/menu-items", adminToken, """
                {"categoryId":"%s","name":"%s %s","prepStation":"KITCHEN","soldByWeight":false,"price":"%s",
                 "serviceChargeEligible":%s}
                """.formatted(categoryId, name, suffix, price, serviceChargeEligible)), 201)
                .get("id").asString();
    }

    String createDiningTable(String label) {
        String full = label + " " + suffix;
        String shortLabel = full.length() > 20 ? full.substring(0, 20) : full;
        return send(post("/api/restaurant/dining-tables", adminToken, "{\"label\":\"%s\"}".formatted(shortLabel)), 201)
                .get("id").asString();
    }

    String openOnTable(String diningTableId) {
        return send(post(TABS, waiterToken,
                        "{\"origin\":\"TABLE_SERVICE\",\"diningTableId\":\"%s\"}".formatted(diningTableId)), 201)
                .get("id").asString();
    }

    /** Orders the item and answers the id of the new line. */
    String order(String tabId, String menuItemId, int quantity) {
        JsonNode tab = send(post(TABS + "/" + tabId + "/items", waiterToken,
                "{\"menuItemId\":\"%s\",\"quantity\":%d}".formatted(menuItemId, quantity)), 201);
        JsonNode items = tab.get("items");
        return items.get(items.size() - 1).get("id").asString();
    }

    HttpRequest.Builder pay(String tabId, String method, String amount, String idempotencyKey) {
        return post(TABS + "/" + tabId + "/payments", waiterToken,
                "{\"method\":\"%s\",\"amount\":\"%s\"}".formatted(method, amount))
                .header("Idempotency-Key", idempotencyKey);
    }

    // ------------------------------------------------------------- concurrency

    /** Runs every task at the same moment, released together by one latch, and waits for all. */
    static <T> List<T> concurrently(List<Callable<T>> tasks) throws Exception {
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

    String codeOf(HttpResponse<String> response) {
        return jsonMapper.readTree(response.body()).get("code").asString();
    }

    // ------------------------------------------------------------- http helpers

    HttpRequest.Builder get(String path, String token) {
        return authorized(path, token).GET();
    }

    HttpRequest.Builder post(String path, String token, String body) {
        return authorized(path, token).POST(HttpRequest.BodyPublishers.ofString(body));
    }

    HttpRequest.Builder put(String path, String token, String body) {
        return authorized(path, token).PUT(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder authorized(String path, String token) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token);
    }

    JsonNode send(HttpRequest.Builder builder, int expectedStatus) {
        HttpResponse<String> response = exchange(builder);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return jsonMapper.readTree(response.body());
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

    String createUser(Role role) {
        String username = "closing_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(User.create(PROPERTY_ID, username, "Closing Test User", PASSWORD, Set.of(role), passwordEncoder));
        return username;
    }

    String accessTokenFor(String username) {
        return new JwtTokenIssuer(jwtSecret, clock)
                .issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }
}
