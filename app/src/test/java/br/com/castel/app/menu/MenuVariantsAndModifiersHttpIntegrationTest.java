package br.com.castel.app.menu;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The vertical slice of task 1.2, end to end: variants and modifiers written through the
 * administration routes, persisted, and read back on the public menu.
 *
 * <p>One scenario, per the test budget of {@code CLAUDE.md}. The rules of the aggregate — starting
 * price, availability with variants, the modifier quantity range — are covered without a database
 * in the unit tests of {@code restaurant}; what this proves is the wiring: the mapping of the two
 * child collections and of the catalog, the V4 column, and the shape of {@code /public/menu}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MenuVariantsAndModifiersHttpIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "menu-variants-test-password";

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

    @Test
    void shouldPublishVariantsFromTheCheapestAndOnlyTheActiveModifiersOfAnItem() {
        String token = accessTokenFor(createAdmin());
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        String categoryId = send(post("/api/restaurant/menu-categories", token,
                        "{\"name\":\"Pizzas %s\",\"displayOrder\":1}".formatted(suffix)), 201)
                .get("id").asString();
        String itemName = "Pizza Calabresa " + suffix;
        String itemId = send(post("/api/restaurant/menu-items", token, """
                        {"categoryId":"%s","name":"%s","prepStation":"PIZZA","soldByWeight":false,"price":"50.00"}
                        """.formatted(categoryId, itemName)), 201)
                .get("id").asString();

        send(post(variantsOf(itemId), token, "{\"name\":\"M\",\"price\":\"55.00\"}"), 201);
        send(post(variantsOf(itemId), token, "{\"name\":\"P\",\"price\":\"45.00\"}"), 201);
        JsonNode withLarge = send(post(variantsOf(itemId), token, "{\"name\":\"G\",\"price\":\"70.00\"}"), 201);
        String largeId = elementNamed(withLarge.get("variants"), "G").get("id").asString();

        String stuffedCrustId = send(post("/api/restaurant/modifiers", token,
                        "{\"name\":\"Borda recheada %s\",\"price\":\"12.00\"}".formatted(suffix)), 201)
                .get("id").asString();
        String noCostId = send(post("/api/restaurant/modifiers", token,
                        "{\"name\":\"Sem cebola %s\",\"price\":\"0.00\"}".formatted(suffix)), 201)
                .get("id").asString();
        String withdrawnLaterId = send(post("/api/restaurant/modifiers", token,
                        "{\"name\":\"Catupiry %s\",\"price\":\"9.00\"}".formatted(suffix)), 201)
                .get("id").asString();
        send(put(modifierOf(itemId, stuffedCrustId), token, "{\"maxQuantity\":1}"), 200);
        send(put(modifierOf(itemId, noCostId), token, "{\"maxQuantity\":1}"), 200);
        send(put(modifierOf(itemId, withdrawnLaterId), token, "{\"maxQuantity\":2}"), 200);
        send(put(modifierOf(itemId, stuffedCrustId), token, "{\"maxQuantity\":2}"), 200);
        send(post("/api/restaurant/modifiers/" + withdrawnLaterId + "/deactivate", token, ""), 200);

        JsonNode administrationView =
                send(post(variantsOf(itemId) + "/" + largeId + "/unavailable", token, ""), 200);

        assertThat(administrationView.get("price").asString()).isEqualTo("50.00");
        assertThat(administrationView.get("modifiers")).hasSize(3);
        assertThat(elementNamed(administrationView.get("modifiers"), "Catupiry " + suffix).get("isActive").asBoolean())
                .isFalse();
        assertThat(elementNamed(administrationView.get("modifiers"), "Sem cebola " + suffix).get("price").asString())
                .isEqualTo("0.00");

        JsonNode item = itemOnThePublicMenu(itemName);

        assertThat(item.get("price").asString()).isEqualTo("45.00");
        assertThat(item.get("availableNow").asBoolean()).isTrue();
        assertThat(names(item.get("variants"))).containsExactly("M", "P", "G");
        assertThat(elementNamed(item.get("variants"), "G").get("availableNow").asBoolean()).isFalse();
        assertThat(elementNamed(item.get("variants"), "P").get("availableNow").asBoolean()).isTrue();
        assertThat(elementNamed(item.get("variants"), "P").get("price").asString()).isEqualTo("45.00");
        assertThat(names(item.get("modifiers")))
                .containsExactly("Borda recheada " + suffix, "Sem cebola " + suffix);
        assertThat(elementNamed(item.get("modifiers"), "Sem cebola " + suffix).get("price").asString())
                .isEqualTo("0.00");
        assertThat(elementNamed(item.get("modifiers"), "Borda recheada " + suffix).get("maxQuantity").asInt())
                .isEqualTo(2);
    }

    // ------------------------------------------------------------- reading the answers

    private JsonNode itemOnThePublicMenu(String itemName) {
        JsonNode menu = send(request("/public/menu").GET(), 200);
        return StreamSupport.stream(menu.get("categories").spliterator(), false)
                .flatMap(category -> StreamSupport.stream(category.get("items").spliterator(), false))
                .filter(item -> item.get("name").asString().equals(itemName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Item " + itemName + " is not on the public menu"));
    }

    private static JsonNode elementNamed(JsonNode array, String name) {
        return StreamSupport.stream(array.spliterator(), false)
                .filter(element -> element.get("name").asString().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No element named " + name + " in " + array));
    }

    private static List<String> names(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(element -> element.get("name").asString())
                .toList();
    }

    // ------------------------------------------------------------- http helpers

    private static String variantsOf(String itemId) {
        return "/api/restaurant/menu-items/" + itemId + "/variants";
    }

    private static String modifierOf(String itemId, String modifierId) {
        return "/api/restaurant/menu-items/" + itemId + "/modifiers/" + modifierId;
    }

    private HttpRequest.Builder post(String path, String token, String body) {
        return authorized(path, token).POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder put(String path, String token, String body) {
        return authorized(path, token).PUT(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder authorized(String path, String token) {
        return request(path)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private JsonNode send(HttpRequest.Builder builder, int expectedStatus) {
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
            return jsonMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    // ------------------------------------------------------------- identity fixtures

    private String createAdmin() {
        String username = "variants_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(User.create(
                PROPERTY_ID, username, "Menu Variants Test User", PASSWORD, Set.of(Role.ADMIN), passwordEncoder));
        return username;
    }

    private String accessTokenFor(String username) {
        return new JwtTokenIssuer(jwtSecret, clock)
                .issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }
}
