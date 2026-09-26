package br.com.castel.app.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.app.support.AbstractIntegrationTest;
import br.com.castel.billing.api.ChargeId;
import br.com.castel.billing.api.ChargeRequest;
import br.com.castel.billing.api.ChargeSource;
import br.com.castel.billing.api.FolioFacade;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.FolioOwner;
import br.com.castel.billing.api.FolioReference;
import br.com.castel.billing.api.FolioStatus;
import br.com.castel.billing.api.FolioView;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import br.com.castel.identity.infra.JwtTokenIssuer;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.Money;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
 * The folio of task 1.3, end to end: opened and posted through the {@link FolioFacade} other modules
 * will call, paid, reversed, adjusted and closed through the routes of the front desk, persisted in
 * the V6 tables.
 *
 * <p>The rules of the aggregate — balance, reversal, closing — are covered without a database in the
 * unit tests of {@code billing}. What this proves is the wiring: the mapping of the four kinds of
 * charge and of the payments, the replay of an idempotency key through the header and against the
 * database, the lookup by reference code, and the split of roles.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FolioHttpIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "folio-test-password";
    private static final String FOLIOS = "/api/billing/folios";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    private FolioFacade folioFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Test
    void shouldCarryAStayFromFirstChargeToClosingWithEachRoleOnItsOwnRoutes() {
        String adminToken = accessTokenFor(createUser(Role.ADMIN));
        String frontDeskToken = accessTokenFor(createUser(Role.FRONT_DESK));
        String waiterToken = accessTokenFor(createUser(Role.WAITER));
        String code = uniqueCode();
        FolioId folioId = folioFacade.openStayFolio(
                FolioOwner.reservation(UUID.randomUUID()), new FolioReference(" " + code + " ", "Room " + code));
        folioFacade.post(folioId, new ChargeRequest(
                Money.of("200.00"), "Room night", ChargeSource.roomNight(UUID.randomUUID())));
        ChargeId tabCharge = folioFacade.post(folioId, new ChargeRequest(
                Money.of("80.00"), "Restaurant - tab 142", ChargeSource.tab(UUID.randomUUID())));
        String folioPath = FOLIOS + "/" + folioId.value();

        JsonNode firstPayment = send(pay(folioPath, frontDeskToken, "PIX", "100.00", uniqueKey()), 201);
        JsonNode secondPayment = send(pay(folioPath, frontDeskToken, "CASH", "50.00", uniqueKey()), 201);

        assertThat(firstPayment.get("balance").asString()).isEqualTo("180.00");
        assertThat(secondPayment.get("balance").asString()).isEqualTo("130.00");

        JsonNode reversed = send(post(folioPath + "/charges/" + tabCharge.value() + "/reversal", frontDeskToken,
                "{\"reason\":\"Posted on the wrong room\"}"), 201);

        assertThat(reversed.get("balance").asString()).isEqualTo("50.00");
        assertThat(reversed.get("charges")).hasSize(3);
        JsonNode original = chargeWithId(reversed, tabCharge.value().toString());
        JsonNode reversal = chargeWithId(reversed, original.get("reversedBy").asString());
        assertThat(original.get("amount").asString()).isEqualTo("80.00");
        assertThat(reversal.get("amount").asString()).isEqualTo("-80.00");
        assertThat(reversal.get("type").asString()).isEqualTo("TAB");
        assertThat(reversal.get("reversalOf").asString()).isEqualTo(tabCharge.value().toString());
        assertThat(reversal.get("reason").asString()).isEqualTo("Posted on the wrong room");

        assertThat(send(post(folioPath + "/adjustments", frontDeskToken, adjustment("-50.00")), 403)
                .get("code").asString()).isEqualTo("ACCESS_DENIED");
        send(get(folioPath, waiterToken), 403);

        JsonNode adjusted = send(post(folioPath + "/adjustments", adminToken, adjustment("-50.00")), 201);

        assertThat(adjusted.get("totalCharges").asString()).isEqualTo("150.00");
        assertThat(adjusted.get("totalPayments").asString()).isEqualTo("150.00");
        assertThat(adjusted.get("balance").asString()).isEqualTo("0.00");
        assertThat(send(get(FOLIOS + "?referenceCode=" + code, frontDeskToken), 200).get("id").asString())
                .isEqualTo(folioId.value().toString());

        JsonNode closed = send(post(folioPath + "/close", frontDeskToken, ""), 200);

        assertThat(closed.get("status").asString()).isEqualTo("CLOSED");
        assertThat(closed.get("closedAt").isNull()).isFalse();
        FolioView view = folioFacade.findById(folioId);
        assertThat(view.status()).isEqualTo(FolioStatus.CLOSED);
        assertThat(view.balance()).isEqualTo(Money.ZERO);
        assertThat(view.charges()).hasSize(4);
        assertThat(send(post(folioPath + "/close", frontDeskToken, ""), 409).get("code").asString())
                .isEqualTo("FOLIO_CLOSED");
    }

    @Test
    void shouldRegisterARetriedPaymentOnceEvenAfterTheFolioCloses() {
        String frontDeskToken = accessTokenFor(createUser(Role.FRONT_DESK));
        FolioId folioId = folioFacade.openTabFolio(FolioOwner.tab(UUID.randomUUID()));
        folioFacade.post(folioId, new ChargeRequest(
                Money.of("100.00"), "Restaurant - tab 7", ChargeSource.tab(UUID.randomUUID())));
        String folioPath = FOLIOS + "/" + folioId.value();
        String key = uniqueKey();

        JsonNode first = send(pay(folioPath, frontDeskToken, "PIX", "60.00", key), 201);
        JsonNode retry = send(pay(folioPath, frontDeskToken, "PIX", "60.00", " " + key + " "), 201);

        assertThat(retry.get("id").asString()).isEqualTo(first.get("id").asString());
        assertThat(retry.get("balance").asString()).isEqualTo("40.00");
        assertThat(paymentsUnder(key)).isEqualTo(1);
        assertThat(send(pay(folioPath, frontDeskToken, "PIX", "70.00", key), 409).get("code").asString())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");

        send(pay(folioPath, frontDeskToken, "CASH", "40.00", uniqueKey()), 201);
        send(post(folioPath + "/close", frontDeskToken, ""), 200);
        JsonNode retryAfterClosing = send(pay(folioPath, frontDeskToken, "PIX", "60.00", key), 201);

        assertThat(retryAfterClosing.get("id").asString()).isEqualTo(first.get("id").asString());
        assertThat(retryAfterClosing.get("balance").asString()).isEqualTo("0.00");
        assertThat(paymentsUnder(key)).isEqualTo(1);
    }

    @Test
    void shouldFindOnlyAnOpenStayByItsCodeAndFreeTheOldCodeOnARoomChange() {
        String oldCode = uniqueCode();
        String newCode = uniqueCode();
        FolioId moving = folioFacade.openStayFolio(
                FolioOwner.reservation(UUID.randomUUID()), new FolioReference(oldCode, "Room " + oldCode));

        folioFacade.changeReference(moving, new FolioReference(newCode, "Room " + newCode));

        assertThat(folioFacade.findOpenStayFolioByCode(oldCode)).isEmpty();
        assertThat(folioFacade.findOpenStayFolioByCode(newCode)).map(FolioView::folioId).contains(moving);

        FolioId nextGuest = folioFacade.openStayFolio(
                FolioOwner.reservation(UUID.randomUUID()), new FolioReference(oldCode, "Room " + oldCode));

        assertThat(folioFacade.findOpenStayFolioByCode(oldCode)).map(FolioView::folioId).contains(nextGuest);

        folioFacade.close(nextGuest);

        assertThat(folioFacade.findOpenStayFolioByCode(oldCode)).isEmpty();
        assertThat(folioFacade.findById(nextGuest).reference()).map(FolioReference::code).contains(oldCode);
    }

    @Test
    void shouldRefuseASecondFolioForTheSameOwner() {
        FolioOwner reservation = FolioOwner.reservation(UUID.randomUUID());
        FolioOwner tab = FolioOwner.tab(UUID.randomUUID());
        folioFacade.openStayFolio(reservation, new FolioReference(uniqueCode(), "Room"));
        folioFacade.openTabFolio(tab);

        assertRefusedWith(
                () -> folioFacade.openStayFolio(reservation, new FolioReference(uniqueCode(), "Room")),
                "FOLIO_ALREADY_OPENED_FOR_OWNER");
        assertRefusedWith(() -> folioFacade.openTabFolio(tab), "FOLIO_ALREADY_OPENED_FOR_OWNER");
    }

    @Test
    void shouldRefuseAReferenceCodeHeldByAnotherOpenStayUntilThatStayCloses() {
        String code = uniqueCode();
        FolioId holder = folioFacade.openStayFolio(
                FolioOwner.reservation(UUID.randomUUID()), new FolioReference(code, "Room " + code));
        FolioId other = folioFacade.openStayFolio(
                FolioOwner.reservation(UUID.randomUUID()), new FolioReference(uniqueCode(), "Room"));

        assertThatThrownBy(() -> folioFacade.openStayFolio(
                        FolioOwner.reservation(UUID.randomUUID()), new FolioReference(code, "Room " + code)))
                .isInstanceOfSatisfying(DomainException.class, refused ->
                        assertThat(refused.code()).isEqualTo("FOLIO_REFERENCE_ALREADY_IN_USE"))
                .hasMessageNotContaining(code);
        assertRefusedWith(
                () -> folioFacade.changeReference(other, new FolioReference(code, "Room " + code)),
                "FOLIO_REFERENCE_ALREADY_IN_USE");

        folioFacade.close(holder);
        FolioId nextGuest = folioFacade.openStayFolio(
                FolioOwner.reservation(UUID.randomUUID()), new FolioReference(code, "Room " + code));

        assertThat(folioFacade.findOpenStayFolioByCode(code)).map(FolioView::folioId).contains(nextGuest);
    }

    @Test
    void shouldRefuseReversingTheSameChargeAgainOnALaterRequest() {
        String frontDeskToken = accessTokenFor(createUser(Role.FRONT_DESK));
        FolioId folioId = folioFacade.openTabFolio(FolioOwner.tab(UUID.randomUUID()));
        ChargeId charge = folioFacade.post(folioId, new ChargeRequest(
                Money.of("30.00"), "Restaurant - tab 9", ChargeSource.tab(UUID.randomUUID())));
        String reversalPath = FOLIOS + "/" + folioId.value() + "/charges/" + charge.value() + "/reversal";
        String reason = "{\"reason\":\"Posted twice\"}";
        send(post(reversalPath, frontDeskToken, reason), 201);

        assertThat(send(post(reversalPath, frontDeskToken, reason), 409).get("code").asString())
                .isEqualTo("CHARGE_ALREADY_REVERSED");
        assertThat(folioFacade.findById(folioId).charges()).hasSize(2);
    }

    @Test
    void shouldRollBackAnAdjustmentThatTakesTheTotalOutOfRangeAndKeepTheFolioReadable() {
        String adminToken = accessTokenFor(createUser(Role.ADMIN));
        FolioId folioId = folioFacade.openTabFolio(FolioOwner.tab(UUID.randomUUID()));
        String folioPath = FOLIOS + "/" + folioId.value();
        send(post(folioPath + "/adjustments", adminToken, adjustment("9999999999.99")), 201);

        assertThat(send(post(folioPath + "/adjustments", adminToken, adjustment("9999999999.99")), 422)
                        .get("code").asString())
                .isEqualTo("MONEY_OUT_OF_RANGE");

        JsonNode folio = send(get(folioPath, adminToken), 200);
        assertThat(folio.get("charges")).hasSize(1);
        assertThat(folio.get("totalCharges").asString()).isEqualTo("9999999999.99");
    }

    // ------------------------------------------------------------- fixtures

    private static JsonNode chargeWithId(JsonNode folio, String chargeId) {
        for (JsonNode charge : folio.get("charges")) {
            if (charge.get("id").asString().equals(chargeId)) {
                return charge;
            }
        }
        throw new AssertionError("No charge " + chargeId + " in " + folio);
    }

    private static void assertRefusedWith(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class, refused ->
                assertThat(refused.code()).isEqualTo(code));
    }

    private int paymentsUnder(String key) {
        return jdbcTemplate.queryForObject("select count(*) from payment where idempotency_key = ?", Integer.class, key);
    }

    private static String uniqueCode() {
        return "R" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }

    private static String adjustment(String amount) {
        return "{\"amount\":\"%s\",\"description\":\"Courtesy\",\"reason\":\"Guest complaint\"}".formatted(amount);
    }

    // ------------------------------------------------------------- http helpers

    private HttpRequest.Builder pay(String folioPath, String token, String method, String amount, String key) {
        return post(folioPath + "/payments", token, "{\"method\":\"%s\",\"amount\":\"%s\"}".formatted(method, amount))
                .header("Idempotency-Key", key);
    }

    private HttpRequest.Builder get(String path, String token) {
        return authorized(path, token).GET();
    }

    private HttpRequest.Builder post(String path, String token, String body) {
        return authorized(path, token).POST(HttpRequest.BodyPublishers.ofString(body));
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
        String username = "folio_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(User.create(PROPERTY_ID, username, "Folio Test User", PASSWORD, Set.of(role), passwordEncoder));
        return username;
    }

    private String accessTokenFor(String username) {
        return new JwtTokenIssuer(jwtSecret, clock).issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }
}
