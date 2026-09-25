package br.com.castel.app.diningtable;

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
 * The vertical slice of task 1.5, end to end: dining tables registered through the routes,
 * persisted in the V5 table and read back in the order of decision #8.
 *
 * <p>One scenario, per the test budget of {@code CLAUDE.md}. The invariants of the aggregate are
 * covered without a database in the unit tests of {@code restaurant}; what this proves is the
 * wiring: the mapping of {@code dining_table}, the order of the list, clearing seats through
 * {@code PUT}, the filter of inactive tables and the split of roles between reading and writing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiningTableHttpIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "dining-table-test-password";
    private static final String DINING_TABLES = "/api/restaurant/dining-tables";

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
    void shouldListActiveTablesByAreaAndNaturalLabelAndLetTheWaiterOnlyRead() {
        String adminToken = accessTokenFor(createUser(Role.ADMIN));
        String waiterToken = accessTokenFor(createUser(Role.WAITER));
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String hall = "Salao " + suffix;
        String veranda = "Varanda " + suffix;

        send(post(DINING_TABLES, adminToken, table("Mesa 10 " + suffix, "4", hall)), 201);
        send(post(DINING_TABLES, adminToken, table("Mesa 2 " + suffix, "2", hall)), 201);
        JsonNode verandaTable = send(post(DINING_TABLES, adminToken, table("V1 " + suffix, "6", veranda)), 201);
        send(post(DINING_TABLES, adminToken, table("Balcao " + suffix, null, null)), 201);
        String verandaTableId = verandaTable.get("id").asString();

        assertThat(verandaTable.get("seats").asInt()).isEqualTo(6);
        assertThat(verandaTable.get("isActive").asBoolean()).isTrue();
        assertThat(labelsOf(send(get(DINING_TABLES, adminToken), 200), suffix))
                .containsExactly("Mesa 2 " + suffix, "Mesa 10 " + suffix, "V1 " + suffix, "Balcao " + suffix);

        JsonNode redescribed = send(put(DINING_TABLES + "/" + verandaTableId, adminToken,
                table("V1 " + suffix, null, veranda)), 200);

        assertThat(redescribed.get("seats").isNull()).isTrue();
        assertThat(redescribed.get("area").asString()).isEqualTo(veranda);

        JsonNode deactivated = send(post(DINING_TABLES + "/" + verandaTableId + "/deactivate", adminToken, ""), 200);

        assertThat(deactivated.get("isActive").asBoolean()).isFalse();
        assertThat(labelsOf(send(get(DINING_TABLES, waiterToken), 200), suffix))
                .containsExactly("Mesa 2 " + suffix, "Mesa 10 " + suffix, "Balcao " + suffix);
        assertThat(labelsOf(send(get(DINING_TABLES + "?includeInactive=true", adminToken), 200), suffix))
                .containsExactly("Mesa 2 " + suffix, "Mesa 10 " + suffix, "V1 " + suffix, "Balcao " + suffix);
        assertThat(send(get(DINING_TABLES + "/" + verandaTableId, waiterToken), 200).get("label").asString())
                .isEqualTo("V1 " + suffix);

        assertThat(send(post(DINING_TABLES, waiterToken, table("Mesa 3 " + suffix, null, null)), 403)
                        .get("code").asString())
                .isEqualTo("ACCESS_DENIED");
        send(get(DINING_TABLES + "?includeInactive=true", waiterToken), 403);
    }

    // ------------------------------------------------------------- reading the answers

    /** The labels of this run's tables, in the order the list gave them. */
    private static List<String> labelsOf(JsonNode list, String suffix) {
        return StreamSupport.stream(list.spliterator(), false)
                .map(element -> element.get("label").asString())
                .filter(label -> label.endsWith(suffix))
                .toList();
    }

    private static String table(String label, String seats, String area) {
        return "{\"label\":\"%s\",\"seats\":%s,\"area\":%s}"
                .formatted(label, seats, area == null ? "null" : "\"" + area + "\"");
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

    private HttpRequest.Builder authorized(String path, String token) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token);
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

    private String createUser(Role role) {
        String username = "tables_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(User.create(
                PROPERTY_ID, username, "Dining Table Test User", PASSWORD, Set.of(role), passwordEncoder));
        return username;
    }

    private String accessTokenFor(String username) {
        return new JwtTokenIssuer(jwtSecret, clock)
                .issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }
}
