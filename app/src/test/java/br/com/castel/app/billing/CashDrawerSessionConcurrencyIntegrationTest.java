package br.com.castel.app.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.app.support.AbstractIntegrationTest;
import br.com.castel.billing.api.ChargeRequest;
import br.com.castel.billing.api.ChargeSource;
import br.com.castel.billing.api.FolioFacade;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.FolioOwner;
import br.com.castel.billing.api.PaymentId;
import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.billing.application.CashDrawerSessionService;
import br.com.castel.billing.application.FolioService;
import br.com.castel.billing.domain.CashDrawerSession;
import br.com.castel.billing.domain.CashDrawerSessionId;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import br.com.castel.identity.infra.JwtTokenIssuer;
import br.com.castel.sharedkernel.Money;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

/**
 * The races of task 2.4, each over several rounds with every side released at the same instant.
 *
 * <p>The third race, a refund during a closing, is described on its test.
 *
 * <p>Several openings at once: the unique index on the open session decides, and every loser answers
 * {@code CASH_DRAWER_SESSION_ALREADY_OPEN}, never a 500 (invariant 3). A cash payment during a closing:
 * the {@code FOR UPDATE} of the closing and the {@code FOR KEY SHARE} of the payment conflict, so the
 * payment either lands before the sum and is frozen into the expected amount, or finds the session
 * closed and falls into none (invariant 26). A payment linked to a closed session without being
 * counted is exactly the failure a {@code FOR NO KEY UPDATE} would let through.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CashDrawerSessionConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int ROUNDS = 5;
    private static final int OPENINGS = 5;

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ExecutorService executor = Executors.newFixedThreadPool(OPENINGS);

    @Autowired
    private FolioFacade folioFacade;

    @Autowired
    private CashDrawerSessionService cashDrawerSessions;

    @Autowired
    private FolioService folioService;

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
    void startWithNoOpenSession() {
        CashDrawerSessionHttpIntegrationTest.closeAnyOpenSession(jdbcTemplate);
    }

    @AfterEach
    void leaveNoOpenSession() {
        executor.shutdownNow();
        CashDrawerSessionHttpIntegrationTest.closeAnyOpenSession(jdbcTemplate);
    }

    @Test
    void shouldOpenOneSessionAndRefuseTheOthersWhenSeveralOpenAtOnce() throws Exception {
        String token = frontDeskToken();
        for (int round = 0; round < ROUNDS; round++) {
            List<Callable<HttpResponse<String>>> openings = new ArrayList<>();
            for (int opening = 0; opening < OPENINGS; opening++) {
                openings.add(() -> open(token));
            }

            List<HttpResponse<String>> responses = runTogether(openings);

            assertThat(responses).filteredOn(response -> response.statusCode() == 201).hasSize(1);
            assertThat(responses).filteredOn(response -> response.statusCode() != 201).allSatisfy(response -> {
                assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
                assertThat(jsonMapper.readTree(response.body()).get("code").asString())
                        .isEqualTo("CASH_DRAWER_SESSION_ALREADY_OPEN");
            });
            assertThat(jdbcTemplate.queryForObject(
                            "select count(*) from cash_drawer_session where status = 'OPEN'", Integer.class))
                    .isEqualTo(1);
            CashDrawerSessionHttpIntegrationTest.closeAnyOpenSession(jdbcTemplate);
        }
    }

    @Test
    void shouldNeverLinkACashPaymentToAClosedSessionWithoutCountingItWhenItRacesTheClosing() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            CashDrawerSessionId sessionId = cashDrawerSessions.open(Money.of("50.00")).session().id();
            FolioId folioId = tabFolioOwing("100.00");
            String key = "key-" + UUID.randomUUID();
            Callable<Object> payment = () ->
                    folioFacade.receivePayment(folioId, PaymentMethod.CASH, Money.of("100.00"), key);
            Callable<Object> closing = () ->
                    cashDrawerSessions.close(sessionId, Money.of("50.00"), "Counted during the race", false);

            runTogether(List.of(payment, closing));

            CashDrawerSession closed = cashDrawerSessions.find(sessionId).session();
            String linkedTo = jdbcTemplate.queryForObject(
                    "select cast(cash_drawer_session_id as varchar) from payment where idempotency_key = ?",
                    String.class, key);
            Money counted = linkedTo == null ? Money.ZERO : Money.of("100.00");
            assertThat(linkedTo).isIn(null, sessionId.value().toString());
            assertThat(closed.frozenCashPayments()).contains(counted);
            assertThat(closed.frozenExpectedAmount()).contains(Money.of("50.00").plus(counted));
        }
    }

    /**
     * Invariant 27: the refund locks the session of the payment {@code FOR KEY SHARE}, so it either
     * commits before the closing sums the payments, or takes its moment after the closing committed.
     * The payment is counted in the frozen amount exactly when it was refunded after the closing.
     * Without the lock, a refund stamped before the closing can commit after the sum and still be
     * counted.
     */
    @Test
    void shouldCountARefundedCashPaymentOnlyWhenTheRefundCameAfterTheClosing() throws Exception {
        for (int round = 0; round < ROUNDS * 2; round++) {
            CashDrawerSessionId sessionId = cashDrawerSessions.open(Money.of("50.00")).session().id();
            FolioId folioId = tabFolioOwing("100.00");
            PaymentId paymentId = folioService.registerPayment(
                    folioId, PaymentMethod.CASH, Money.of("100.00"), "key-" + UUID.randomUUID()).payment().id();
            Callable<Object> refund = () -> folioService.refundPayment(folioId, paymentId, "Paid by mistake");
            Callable<Object> closing = () ->
                    cashDrawerSessions.close(sessionId, Money.of("50.00"), "Counted during the race", false);

            runTogether(List.of(refund, closing));

            CashDrawerSession closed = cashDrawerSessions.find(sessionId).session();
            Instant refundedAt = jdbcTemplate.queryForObject(
                    "select refunded_at from payment where id = ?", Instant.class, paymentId.value());
            Money counted = refundedAt.isAfter(closed.closedAt().orElseThrow()) ? Money.of("100.00") : Money.ZERO;
            assertThat(closed.frozenCashPayments()).as("refunded at %s, closed at %s", refundedAt, closed.closedAt())
                    .contains(counted);
        }
    }

    // ------------------------------------------------------------- racing

    /** Starts every task and releases them at the same instant. */
    private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        return results;
    }

    // ------------------------------------------------------------- fixtures

    private FolioId tabFolioOwing(String amount) {
        FolioId folioId = folioFacade.openTabFolio(FolioOwner.tab(UUID.randomUUID()));
        folioFacade.post(folioId, new ChargeRequest(Money.of(amount), "Restaurant - tab", ChargeSource.tab(UUID.randomUUID())));
        return folioId;
    }

    private HttpResponse<String> open(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/billing/cash-sessions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("{\"openingFloat\":\"100.00\"}"))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String frontDeskToken() {
        String username = "cash_race_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(User.create(
                PROPERTY_ID, username, "Cash Race User", "cash-race-password", Set.of(Role.FRONT_DESK), passwordEncoder));
        return new JwtTokenIssuer(jwtSecret, clock).issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }
}
