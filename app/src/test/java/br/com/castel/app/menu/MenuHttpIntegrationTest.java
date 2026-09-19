package br.com.castel.app.menu;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.app.support.AbstractIntegrationTest;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import br.com.castel.identity.infra.JwtTokenIssuer;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The vertical slice of task 0.8, end to end: the administration creates the menu and anyone reads
 * it without a token.
 *
 * <p>Kept to what the slice has to prove, per the test budget of {@code CLAUDE.md}. The invariants
 * of the aggregate — pricing, the schedule, the window crossing midnight — are covered without a
 * database in {@code MenuItemTest}, and the error bodies in {@code ErrorResponseHttpIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MenuHttpIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "menu-test-password";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    private String adminToken;

    @BeforeEach
    void createAdminToken() {
        adminToken = accessTokenFor(createUser(Role.ADMIN));
    }

    @Test
    @DisplayName("the administration creates a category and an item, and anyone reads them")
    void shouldPublishACreatedItemOnThePublicMenu() {
        String categoryId = createCategory("Pizzas " + suffix());
        String itemName = "Pizza Margherita " + suffix();

        HttpResult created = postAsAdmin(
                "/api/restaurant/menu-items",
                """
                {"categoryId":"%s","name":"%s","description":"Molho, mucarela e manjericao",
                 "prepStation":"PIZZA","soldByWeight":false,"price":"62.00"}
                """
                        .formatted(categoryId, itemName));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body()).contains("\"price\":\"62.00\"");

        HttpResult menu = getAnonymous("/public/menu");

        assertThat(menu.status()).isEqualTo(200);
        assertThat(menu.body()).contains(itemName).contains("\"price\":\"62.00\"");
    }

    @Test
    @DisplayName("the public menu answers without a token")
    void shouldAnswerThePublicMenuWithoutAuthentication() {
        HttpResult menu = getAnonymous("/public/menu");

        assertThat(menu.status()).isEqualTo(200);
    }

    @Test
    @DisplayName("the public menu does not leak what only the operation needs")
    void shouldNotCarryOperationalFieldsOnThePublicMenu() {
        String categoryId = createCategory("Bebidas " + suffix());
        postAsAdmin(
                "/api/restaurant/menu-items",
                """
                {"categoryId":"%s","name":"Suco %s","prepStation":"BAR","soldByWeight":false,"price":"12.00"}
                """
                        .formatted(categoryId, suffix()));

        HttpResult menu = getAnonymous("/public/menu");

        assertThat(menu.body())
                .doesNotContain("prepStation")
                .doesNotContain("isActive")
                .doesNotContain("createdBy");
    }

    @Test
    @DisplayName("a waiter cannot create a menu item")
    void shouldRefuseAMenuItemCreatedByAWaiter() {
        String waiterToken = accessTokenFor(createUser(Role.WAITER));

        HttpResult response = post(
                "/api/restaurant/menu-items",
                waiterToken,
                """
                {"categoryId":"%s","name":"Nada","prepStation":"BAR","soldByWeight":false,"price":"1.00"}
                """
                        .formatted(UUID.randomUUID()));

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body()).contains("ACCESS_DENIED");
    }

    // ------------------------------------------------------------- fixtures

    private String createCategory(String name) {
        HttpResult response = postAsAdmin(
                "/api/restaurant/menu-categories", "{\"name\":\"%s\",\"displayOrder\":1}".formatted(name));
        assertThat(response.status()).isEqualTo(201);
        return response.body().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    // ------------------------------------------------------------- http helpers

    private record HttpResult(int status, String body) {}

    private HttpResult postAsAdmin(String path, String body) {
        return post(path, adminToken, body);
    }

    private HttpResult post(String path, String token, String body) {
        return send(request(path)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpResult getAnonymous(String path) {
        return send(request(path).GET());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResult send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String createUser(Role role) {
        String username = "menu_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(User.create(PROPERTY_ID, username, "Menu Test User", PASSWORD, Set.of(role), passwordEncoder));
        return username;
    }

    private String accessTokenFor(String username) {
        return new JwtTokenIssuer(jwtSecret, clock)
                .issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }
}
