package br.com.castel.app.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.app.support.AbstractIntegrationTest;
import br.com.castel.billing.api.ChargeRequest;
import br.com.castel.billing.api.ChargeSource;
import br.com.castel.billing.api.FolioFacade;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.FolioOwner;
import br.com.castel.billing.api.PaymentId;
import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.billing.application.CashDrawerSessionService;
import br.com.castel.billing.application.CashDrawerSessionWithTotals;
import br.com.castel.billing.application.FolioService;
import br.com.castel.billing.domain.CashDrawerSession;
import br.com.castel.billing.domain.CashDrawerSessionId;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import br.com.castel.identity.infra.JwtTokenIssuer;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.Money;
import br.com.castel.sharedkernel.Setting;
import br.com.castel.sharedkernel.SettingRepository;
import br.com.castel.sharedkernel.SettingValueType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * The cash drawer of task 2.4, end to end: a session opened, fed and closed through the routes of the
 * front desk, the cash payments of the folios falling into it, and the setting that makes an open
 * session mandatory.
 *
 * <p>The arithmetic of the expected amount and the difference is covered without a database in the
 * unit tests of {@code billing}. What this proves is the wiring: the link a cash payment gets in the
 * database, the sum the closing reads, the blind closing per role, and what a refund does before and
 * after the closing.
 *
 * <p>The property holds one open session at a time, and the other billing tests pay in cash, so each
 * test starts and ends with no open session.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CashDrawerSessionHttpIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "cash-test-password";
    private static final String SESSIONS = "/api/billing/cash-sessions";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    private FolioFacade folioFacade;

    @Autowired
    private FolioService folioService;

    @Autowired
    private CashDrawerSessionService cashDrawerSessions;

    @Autowired
    private SettingRepository settingRepository;

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

    @BeforeEach
    @AfterEach
    void leaveNoSessionOpenAndCashControlOff() {
        closeAnyOpenSession(jdbcTemplate);
        settingRepository.save(Setting.of(CASH_DRAWER_REQUIRED_SETTING, "false", SettingValueType.BOOLEAN));
    }

    @Test
    void shouldCarryASessionFromOpeningToABlindClosingWithTheCashPaymentsOfTheFolios() {
        String frontDeskToken = accessTokenFor(createUser(Role.FRONT_DESK));
        String otherFrontDeskToken = accessTokenFor(createUser(Role.FRONT_DESK));
        String adminToken = accessTokenFor(createUser(Role.ADMIN));

        JsonNode opened = send(post(SESSIONS, frontDeskToken, "{\"openingFloat\":\"100.00\"}"), 201);
        String sessionPath = SESSIONS + "/" + opened.get("id").asString();

        assertThat(opened.get("status").asString()).isEqualTo("OPEN");
        assertThat(opened.get("expectedAmount").isNull()).isTrue();
        send(movement(sessionPath + "/supplies", frontDeskToken, "50.00", "Change for the evening", uniqueKey()), 201);

        FolioId folioId = tabFolioOwing("200.00");
        String folioPath = "/api/billing/folios/" + folioId.value();
        JsonNode cash = send(pay(folioPath, frontDeskToken, "CASH", "150.00"), 201);
        JsonNode pix = send(pay(folioPath, frontDeskToken, "PIX", "50.00"), 201);

        assertThat(sessionOfPayment(cash)).isEqualTo(opened.get("id").asString());
        assertThat(sessionOfPayment(pix)).isNull();
        JsonNode blind = send(get(sessionPath, frontDeskToken), 200);
        JsonNode seenByAdmin = send(get(SESSIONS + "/current", adminToken), 200);
        assertThat(blind.get("cashPaymentsTotal").isNull()).isTrue();
        assertThat(blind.get("expectedAmount").isNull()).isTrue();
        assertThat(blind.get("cashPaymentCount").asLong()).isEqualTo(1);
        assertThat(seenByAdmin.get("cashPaymentsTotal").asString()).isEqualTo("150.00");
        assertThat(seenByAdmin.get("expectedAmount").asString()).isEqualTo("300.00");

        String dropKey = uniqueKey();
        send(movement(sessionPath + "/drops", frontDeskToken, "200.00", "To the safe", dropKey), 201);
        JsonNode replayed = send(movement(sessionPath + "/drops", frontDeskToken, "200.00", "To the safe", dropKey), 201);

        assertThat(replayed.get("movements")).hasSize(2);
        assertThat(replayed.get("totalDrops").asString()).isEqualTo("200.00");
        assertThat(codeOf(send(close(sessionPath, otherFrontDeskToken, "99.50", "Counted"), 409)))
                .isEqualTo("CASH_DRAWER_SESSION_NOT_OWNED");
        assertThat(codeOf(send(close(sessionPath, frontDeskToken, "99.50", null), 422)))
                .isEqualTo("CASH_CLOSING_NOTE_REQUIRED");

        JsonNode closed = send(close(sessionPath, frontDeskToken, "99.50", "Fifty cents short"), 200);

        assertThat(closed.get("status").asString()).isEqualTo("CLOSED");
        assertThat(closed.get("expectedAmount").asString()).isEqualTo("100.00");
        assertThat(closed.get("countedAmount").asString()).isEqualTo("99.50");
        assertThat(closed.get("difference").asString()).isEqualTo("-0.50");
        assertThat(closed.get("closingNote").asString()).isEqualTo("Fifty cents short");
        JsonNode reread = send(get(sessionPath, frontDeskToken), 200);
        assertThat(reread.get("expectedAmount").asString()).isEqualTo("100.00");
        assertThat(reread.get("cashPaymentsTotal").asString()).isEqualTo("150.00");
        assertThat(codeOf(send(movement(sessionPath + "/drops", adminToken, "1.00", "Late", uniqueKey()), 409)))
                .isEqualTo("CASH_DRAWER_SESSION_CLOSED");
        assertThat(codeOf(send(get(SESSIONS + "/current", frontDeskToken), 404)))
                .isEqualTo("CASH_DRAWER_SESSION_NOT_FOUND");
    }

    /**
     * Decision #18: a drop and a supply cancel out through their signs, so the float plus a supply at
     * the top of the range is still representable after a drop; and a movement that would take the
     * expected amount beyond the range is refused before it commits, leaving the session closable.
     */
    @Test
    void shouldRefuseAMovementThatTakesTheExpectedAmountOutOfRangeAndKeepTheSessionClosable() {
        String adminToken = accessTokenFor(createUser(Role.ADMIN));
        String sessionPath = SESSIONS + "/"
                + send(post(SESSIONS, adminToken, "{\"openingFloat\":\"100.00\"}"), 201).get("id").asString();
        send(movement(sessionPath + "/drops", adminToken, "100.00", "To the safe", uniqueKey()), 201);
        send(movement(sessionPath + "/supplies", adminToken, "9999999999.99", "Typed by mistake", uniqueKey()), 201);

        JsonNode refused = send(movement(sessionPath + "/supplies", adminToken, "0.01", "One cent more", uniqueKey()), 422);

        assertThat(codeOf(refused)).isEqualTo("MONEY_OUT_OF_RANGE");
        JsonNode open = send(get(sessionPath, adminToken), 200);
        assertThat(open.get("movements")).hasSize(2);
        assertThat(open.get("expectedAmount").asString()).isEqualTo("9999999999.99");
        JsonNode closed = send(close(sessionPath, adminToken, "100.00", "Supply typed by mistake"), 200);
        assertThat(closed.get("difference").asString()).isEqualTo("-9999999899.99");
        assertThat(closed.get("cashPaymentsTotal").asString()).isEqualTo("0.00");
    }

    @Test
    void shouldKeepTheWaiterAndTheKitchenOutOfTheCashDrawer() {
        for (Role role : Set.of(Role.WAITER, Role.KITCHEN)) {
            String token = accessTokenFor(createUser(role));

            send(get(SESSIONS + "/current", token), 403);
            send(post(SESSIONS, token, "{\"openingFloat\":\"100.00\"}"), 403);
        }
    }

    @Test
    void shouldRefuseCashWithNoOpenSessionOnlyWhileCashControlIsOnAndStillAnswerARetry() {
        settingRepository.save(Setting.of(CASH_DRAWER_REQUIRED_SETTING, "true", SettingValueType.BOOLEAN));
        FolioId folioId = tabFolioOwing("100.00");
        CashDrawerSessionId sessionId = cashDrawerSessions.open(Money.of("10.00")).session().id();
        String key = uniqueKey();
        PaymentId paid = folioService.registerPayment(folioId, PaymentMethod.CASH, Money.of("30.00"), key)
                .payment().id();
        cashDrawerSessions.close(sessionId, Money.of("40.00"), null, false);

        PaymentId retried = folioService.registerPayment(folioId, PaymentMethod.CASH, Money.of("30.00"), key)
                .payment().id();

        assertThat(retried).isEqualTo(paid);
        assertThatThrownBy(() -> folioFacade.receivePayment(folioId, PaymentMethod.CASH, Money.of("20.00"), uniqueKey()))
                .isInstanceOfSatisfying(DomainException.class, refused ->
                        assertThat(refused.code()).isEqualTo("CASH_DRAWER_SESSION_NOT_OPEN"));
        assertThat(folioFacade.receivePayment(folioId, PaymentMethod.PIX, Money.of("20.00"), uniqueKey()).balance())
                .isEqualTo(Money.of("50.00"));
    }

    @Test
    void shouldLowerTheExpectedAmountOnARefundWhileOpenAndLeaveTheFrozenOneAlone() {
        CashDrawerSessionId sessionId = cashDrawerSessions.open(Money.of("20.00")).session().id();
        FolioId before = tabFolioOwing("100.00");
        PaymentId refundedWhileOpen = folioService.registerPayment(
                before, PaymentMethod.CASH, Money.of("100.00"), uniqueKey()).payment().id();

        folioService.refundPayment(before, refundedWhileOpen, "Paid twice");

        assertThat(expectedAmountOf(sessionId)).isEqualTo(Money.of("20.00"));
        FolioId after = tabFolioOwing("40.00");
        PaymentId refundedAfterClosing = folioService.registerPayment(
                after, PaymentMethod.CASH, Money.of("40.00"), uniqueKey()).payment().id();
        cashDrawerSessions.close(sessionId, Money.of("60.00"), null, false);

        folioService.refundPayment(after, refundedAfterClosing, "Returned the next morning");

        CashDrawerSession closed = cashDrawerSessions.find(sessionId).session();
        assertThat(closed.frozenExpectedAmount()).contains(Money.of("60.00"));
        assertThat(closed.difference()).contains(Money.ZERO);
    }

    // ------------------------------------------------------------- fixtures

    /** Closes whatever session a test left open, straight in the table: no rule of the session is under test here. */
    static void closeAnyOpenSession(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("update cash_drawer_session set status = 'CLOSED', closed_at = now(), closed_by = opened_by, "
                + "expected_amount = opening_float, counted_amount = opening_float where status = 'OPEN'");
    }

    private Money expectedAmountOf(CashDrawerSessionId sessionId) {
        CashDrawerSessionWithTotals reading = cashDrawerSessions.find(sessionId);
        return reading.session().expectedAmount(reading.cashPayments().total());
    }

    private FolioId tabFolioOwing(String amount) {
        FolioId folioId = folioFacade.openTabFolio(FolioOwner.tab(UUID.randomUUID()));
        folioFacade.post(folioId, new ChargeRequest(Money.of(amount), "Restaurant - tab", ChargeSource.tab(UUID.randomUUID())));
        return folioId;
    }

    private String sessionOfPayment(JsonNode payment) {
        return jdbcTemplate.queryForObject(
                "select cast(cash_drawer_session_id as varchar) from payment where id = ?",
                String.class, UUID.fromString(payment.get("id").asString()));
    }

    private static String codeOf(JsonNode problem) {
        return problem.get("code").asString();
    }

    private static String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }

    // ------------------------------------------------------------- http helpers

    private HttpRequest.Builder pay(String folioPath, String token, String method, String amount) {
        return post(folioPath + "/payments", token, "{\"method\":\"%s\",\"amount\":\"%s\"}".formatted(method, amount))
                .header("Idempotency-Key", uniqueKey());
    }

    private HttpRequest.Builder movement(String path, String token, String amount, String reason, String key) {
        return post(path, token, "{\"amount\":\"%s\",\"reason\":\"%s\"}".formatted(amount, reason))
                .header("Idempotency-Key", key);
    }

    private HttpRequest.Builder close(String sessionPath, String token, String counted, String note) {
        String noteJson = note == null ? "null" : "\"" + note + "\"";
        return post(sessionPath + "/close", token, "{\"countedAmount\":\"%s\",\"note\":%s}".formatted(counted, noteJson));
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
        String username = "cash_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(User.create(PROPERTY_ID, username, "Cash Test User", PASSWORD, Set.of(role), passwordEncoder));
        return username;
    }

    private String accessTokenFor(String username) {
        return new JwtTokenIssuer(jwtSecret, clock).issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }
}
