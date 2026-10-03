package br.com.castel.app.finance;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.app.support.AbstractIntegrationTest;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import br.com.castel.identity.infra.JwtTokenIssuer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
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
 * Money going out, against a real PostgreSQL (task F1).
 *
 * <p>What only the database can prove: the row is actually written with both dates; the check
 * constraints of {@code V12} accept every legitimate row and refuse the illegitimate ones; and a
 * cash expense and the movement of the drawer are written in the <b>same transaction</b>, so the
 * blind closing still reconciles (decision D4). The aggregate alone would pass with none of that.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExpenseHttpIntegrationTest extends AbstractIntegrationTest {

    private static final String EXPENSES = "/api/finance/expenses";
    private static final String PAYABLES = "/api/finance/payables";
    private static final String SESSIONS = "/api/billing/cash-sessions";
    private static final String PASSWORD = "finance-test-password";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Clock clock;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    private String adminToken;
    private String waiterToken;
    private String suffix;

    @BeforeEach
    void prepare() {
        adminToken = accessTokenFor(createUser(Role.ADMIN));
        waiterToken = accessTokenFor(createUser(Role.WAITER));
        suffix = UUID.randomUUID().toString().substring(0, 8);
        closeAnyOpenSession();
    }

    @Test
    void shouldWriteTheRowWithBothDatesAndReadItBack() {
        JsonNode created = send(post(EXPENSES, adminToken, """
                {"category":"RENT","description":"Aluguel %s","amount":"3000.00",
                 "accrualDate":"2026-10-01","dueDate":"2026-10-10","supplierName":"Imobiliaria"}
                """.formatted(suffix)), 201);

        String id = created.get("id").asString();
        assertThat(created.get("paid").asBoolean()).isFalse();
        assertThat(created.get("payable").asBoolean()).isTrue();
        assertThat(row(id, "cast(accrual_date as varchar)")).isEqualTo("2026-10-01");
        assertThat(row(id, "cast(due_date as varchar)")).isEqualTo("2026-10-10");
        assertThat(row(id, "cast(paid_at as varchar)")).as("owed, so no payment yet").isNull();
        assertThat(new BigDecimal(row(id, "cast(amount as varchar)"))).isEqualByComparingTo("3000.00");
    }

    @Test
    void shouldKeepTheAccrualMonthWhenTheMoneyLeavesLater() {
        String id = send(post(EXPENSES, adminToken, """
                {"category":"RENT","description":"Aluguel %s","amount":"3000.00","accrualDate":"2026-10-01"}
                """.formatted(suffix)), 201).get("id").asString();

        JsonNode paid = send(post(EXPENSES + "/" + id + "/payment", adminToken, "{\"method\":\"PIX\"}"), 200);

        assertThat(paid.get("accrualDate").asString())
                .as("the month it belongs to does not move when it is paid")
                .isEqualTo("2026-10-01");
        assertThat(paid.get("paidAt").asString()).isNotBlank();
        assertThat(row(id, "cast(accrual_date as varchar)")).isEqualTo("2026-10-01");
        assertThat(row(id, "cast(paid_at as varchar)")).isNotNull();
    }

    /** Decision D4, and the reason it exists: a shortfall in the drawer looks like theft. */
    @Test
    void shouldTakeCashOutOfTheDrawerSoTheBlindClosingStillReconciles() {
        String sessionId = send(post(SESSIONS, adminToken, "{\"openingFloat\":\"500.00\"}"), 201)
                .get("id").asString();

        JsonNode expense = send(post(EXPENSES, adminToken, """
                {"category":"SUPPLIER","description":"Peixe %s","amount":"200.00","method":"CASH"}
                """.formatted(suffix)).header("Idempotency-Key", "fish-" + suffix), 201);

        assertThat(expense.get("cashDrawerSessionId").asString()).isEqualTo(sessionId);
        JsonNode session = send(get(SESSIONS + "/" + sessionId, adminToken), 200);
        assertThat(session.get("expectedAmount").asString())
                .as("500 in the float, 200 paid out")
                .isEqualTo("300.00");
        assertThat(movementTypesOf(sessionId)).containsExactly("EXPENSE_PAYMENT");

        JsonNode closed = send(post(SESSIONS + "/" + sessionId + "/close", adminToken,
                "{\"countedAmount\":\"300.00\"}"), 200);
        assertThat(closed.get("difference").asString())
                .as("no shortfall: the expense is in the drawer's arithmetic")
                .isEqualTo("0.00");
    }

    @Test
    void shouldRefuseCashWithoutAnOpenDrawer() {
        HttpResponse<String> response = exchange(post(EXPENSES, adminToken, """
                {"category":"SUPPLIER","description":"Peixe %s","amount":"200.00","method":"CASH"}
                """.formatted(suffix)).header("Idempotency-Key", "nodrawer-" + suffix));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        assertThat(codeOf(response)).isEqualTo("CASH_EXPENSE_REQUIRES_DRAWER_SESSION");
    }

    /**
     * The expense and the drawer movement are one transaction: if the drawer refuses, no expense
     * row may be left behind.
     */
    @Test
    void shouldLeaveNoExpenseBehindWhenTheDrawerRefusesTheKey() {
        String sessionId = send(post(SESSIONS, adminToken, "{\"openingFloat\":\"500.00\"}"), 201)
                .get("id").asString();
        String sharedKey = "shared-" + suffix;
        send(post(EXPENSES, adminToken, """
                {"category":"SUPPLIER","description":"Primeira %s","amount":"50.00","method":"CASH"}
                """.formatted(suffix)).header("Idempotency-Key", sharedKey), 201);
        long before = expenseCount();

        /* The same key with a different amount is not a retry; the drawer refuses it. */
        HttpResponse<String> response = exchange(post(EXPENSES, adminToken, """
                {"category":"SUPPLIER","description":"Segunda %s","amount":"70.00","method":"CASH"}
                """.formatted(suffix)).header("Idempotency-Key", sharedKey));

        assertThat(response.statusCode()).as(response.body()).isGreaterThanOrEqualTo(400);
        assertThat(expenseCount())
                .as("the refusal rolled the expense back with the movement")
                .isEqualTo(before);
        assertThat(send(get(SESSIONS + "/" + sessionId, adminToken), 200).get("expectedAmount").asString())
                .isEqualTo("450.00");
    }

    @Test
    void shouldCancelWithoutDeletingTheRow() {
        String id = send(post(EXPENSES, adminToken, """
                {"category":"OTHER","description":"Errada %s","amount":"10.00"}
                """.formatted(suffix)), 201).get("id").asString();

        JsonNode cancelled = send(post(EXPENSES + "/" + id + "/cancel", adminToken,
                "{\"reason\":\"lancada em duplicidade\"}"), 200);

        assertThat(cancelled.get("cancellationReason").asString()).isEqualTo("lancada em duplicidade");
        assertThat(cancelled.get("payable").asBoolean()).isFalse();
        assertThat(row(id, "cast(id as varchar)"))
                .as("nothing is deleted in this system")
                .isEqualTo(id);
        assertThat(row(id, "cancellation_reason")).isEqualTo("lancada em duplicidade");
    }

    @Test
    void shouldListTheSameExpenseByAccrualAndByPaymentPeriod() {
        String id = send(post(EXPENSES, adminToken, """
                {"category":"UTILITIES","description":"Luz %s","amount":"800.00","accrualDate":"2026-10-01"}
                """.formatted(suffix)), 201).get("id").asString();
        send(post(EXPENSES + "/" + id + "/payment", adminToken, "{\"method\":\"PIX\"}"), 200);

        JsonNode byAccrual = send(get(EXPENSES + "?from=2026-10-01&to=2026-10-31&by=ACCRUAL", adminToken), 200);
        JsonNode byPayment = send(get(EXPENSES + "?from=2020-01-01&to=2099-12-31&by=PAYMENT", adminToken), 200);

        assertThat(idsOf(byAccrual)).contains(id);
        assertThat(idsOf(byPayment)).contains(id);
        JsonNode owed = send(post(EXPENSES, adminToken, """
                {"category":"UTILITIES","description":"Agua %s","amount":"120.00","accrualDate":"2026-10-01"}
                """.formatted(suffix)), 201);
        assertThat(idsOf(send(get(EXPENSES + "?from=2020-01-01&to=2099-12-31&by=PAYMENT", adminToken), 200)))
                .as("what was never paid is not cash flow")
                .doesNotContain(owed.get("id").asString());
    }

    @Test
    void shouldListWhatIsOwedAndDropItOncePaid() {
        String id = send(post(EXPENSES, adminToken, """
                {"category":"TAX","description":"Imposto %s","amount":"430.00",
                 "accrualDate":"2026-10-01","dueDate":"2026-10-20"}
                """.formatted(suffix)), 201).get("id").asString();

        assertThat(idsOf(send(get(PAYABLES, adminToken), 200))).contains(id);
        send(post(EXPENSES + "/" + id + "/payment", adminToken, "{\"method\":\"PIX\"}"), 200);
        assertThat(idsOf(send(get(PAYABLES, adminToken), 200))).doesNotContain(id);
    }

    @Test
    void shouldRefuseAnEmployeeOnAnythingButPayroll() {
        HttpResponse<String> response = exchange(post(EXPENSES, adminToken, """
                {"category":"RENT","description":"Aluguel %s","amount":"100.00","employeeId":"%s"}
                """.formatted(suffix, UUID.randomUUID())));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(422);
        assertThat(codeOf(response)).isEqualTo("INVALID_EXPENSE_EMPLOYEE");
    }

    @Test
    void shouldReachTheRoutesOnlyAsAdmin() {
        assertThat(exchange(get(EXPENSES + "?from=2026-10-01&to=2026-10-31", waiterToken)).statusCode())
                .isEqualTo(403);
        assertThat(exchange(get(PAYABLES, waiterToken)).statusCode()).isEqualTo(403);
        assertThat(exchange(post(EXPENSES, waiterToken, """
                {"category":"OTHER","description":"Nao %s","amount":"10.00"}
                """.formatted(suffix))).statusCode()).isEqualTo(403);
    }

    // ------------------------------------------------------------- database

    /** One column of the row, read straight from the table. */
    private String row(String id, String column) {
        return queryOne("select " + column + " from expense where id = ?", UUID.fromString(id));
    }

    private long expenseCount() {
        return Long.parseLong(queryOne("select cast(count(*) as varchar) from expense where property_id = ?",
                PROPERTY_ID));
    }

    private java.util.List<String> movementTypesOf(String sessionId) {
        java.util.List<String> types = new java.util.ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select movement_type from cash_movement where cash_drawer_session_id = ?")) {
            statement.setObject(1, UUID.fromString(sessionId));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    types.add(rows.getString(1));
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
        return types;
    }

    private String queryOne(String sql, Object parameter) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Each test starts with the drawer closed, so the cash cases are independent. */
    private void closeAnyOpenSession() {
        HttpResponse<String> current = exchange(get(SESSIONS + "/current", adminToken));
        if (current.statusCode() != 200) {
            return;
        }
        JsonNode session = jsonMapper.readTree(current.body());
        String expected = session.get("expectedAmount").isNull() ? "0.00" : session.get("expectedAmount").asString();
        exchange(post(SESSIONS + "/" + session.get("id").asString() + "/close", adminToken,
                "{\"countedAmount\":\"%s\"}".formatted(expected)));
    }

    // ------------------------------------------------------------- helpers

    private static java.util.List<String> idsOf(JsonNode list) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        list.get("expenses").forEach(expense -> ids.add(expense.get("id").asString()));
        return ids;
    }

    private String codeOf(HttpResponse<String> response) {
        return jsonMapper.readTree(response.body()).get("code").asString();
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

    private String createUser(Role role) {
        String username = "finance_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(
                User.create(PROPERTY_ID, username, "Finance Test User", PASSWORD, Set.of(role), passwordEncoder));
        return username;
    }

    private String accessTokenFor(String username) {
        return new JwtTokenIssuer(jwtSecret, clock)
                .issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }
}
