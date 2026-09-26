package br.com.castel.app.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.app.support.AbstractIntegrationTest;
import br.com.castel.billing.api.ChargeRequest;
import br.com.castel.billing.api.ChargeSource;
import br.com.castel.billing.api.FolioFacade;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.FolioOwner;
import br.com.castel.billing.api.FolioStatus;
import br.com.castel.billing.api.FolioView;
import br.com.castel.billing.application.FolioService;
import br.com.castel.billing.domain.PaymentMethod;
import br.com.castel.identity.api.Role;
import br.com.castel.identity.domain.User;
import br.com.castel.identity.domain.UserRepository;
import br.com.castel.identity.infra.JwtTokenIssuer;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.Money;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The two races of task 1.3 (invariant 25, decision #15), each run over several rounds with both
 * sides released at the same instant: a retry that lands while the first attempt is still being
 * written, and a charge posted while the folio closes. The lock on the folio row serializes them;
 * without it, the first race writes the payment twice or fails on the unique key, and the second
 * closes a folio with a balance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FolioConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int ROUNDS = 5;

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Autowired
    private FolioFacade folioFacade;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @AfterEach
    void stopThreads() {
        executor.shutdownNow();
    }

    @Test
    void shouldRegisterOnePaymentWhenTheSameKeyArrivesTwiceAtOnce() throws Exception {
        String token = frontDeskToken();
        for (int round = 0; round < ROUNDS; round++) {
            FolioId folioId = tabFolioOwing("100.00");
            String key = "key-" + UUID.randomUUID();
            Callable<HttpResponse<String>> payment = () -> pay(token, folioId, key);

            List<HttpResponse<String>> responses = runTogether(payment, payment);

            assertThat(responses).allSatisfy(response ->
                    assertThat(response.statusCode()).as(response.body()).isEqualTo(201));
            assertThat(idOf(responses.get(0))).isEqualTo(idOf(responses.get(1)));
            assertThat(jdbcTemplate.queryForObject(
                            "select count(*) from payment where idempotency_key = ?", Integer.class, key))
                    .isEqualTo(1);
            assertThat(folioFacade.balanceOf(folioId)).isEqualTo(Money.of("50.00"));
        }
    }

    /**
     * The lock on each folio does not serialize two folios: the unique key on the payment decides,
     * and the loser must answer the rule's code, never a 500.
     */
    @Test
    void shouldAcceptTheKeyOnOneFolioAndRefuseItOnTheOtherWhenTwoFoliosRaceForIt() throws Exception {
        String token = frontDeskToken();
        for (int round = 0; round < ROUNDS; round++) {
            FolioId first = tabFolioOwing("100.00");
            FolioId second = tabFolioOwing("100.00");
            String key = "key-" + UUID.randomUUID();

            List<HttpResponse<String>> responses = runTogether(() -> pay(token, first, key), () -> pay(token, second, key));

            assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201, 409);
            HttpResponse<String> refused = responses.stream()
                    .filter(response -> response.statusCode() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(jsonMapper.readTree(refused.body()).get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
            assertThat(jdbcTemplate.queryForObject(
                            "select count(*) from payment where idempotency_key = ?", Integer.class, key))
                    .isEqualTo(1);
        }
    }

    @Test
    void shouldNeverLeaveAClosedFolioWithABalanceWhenAChargeRacesTheClosing() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            FolioId folioId = tabFolioOwing("100.00");
            folioService.receivePayment(folioId, PaymentMethod.CASH, Money.of("100.00"), "key-" + UUID.randomUUID());
            Callable<Boolean> posting = () -> succeeds(() -> folioFacade.post(folioId, tabCharge("30.00")));
            Callable<Boolean> closing = () -> succeeds(() -> folioFacade.close(folioId));

            List<Boolean> outcomes = runTogether(posting, closing);

            FolioView folio = folioFacade.findById(folioId);
            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
            if (folio.status() == FolioStatus.CLOSED) {
                assertThat(folio.balance()).isEqualTo(Money.ZERO);
                assertThat(folio.charges()).hasSize(1);
            } else {
                assertThat(folio.balance()).isEqualTo(Money.of("30.00"));
                assertThat(folio.charges()).hasSize(2);
            }
        }
    }

    /**
     * The lock must read the folio as it stands once locked, even when the caller's transaction had
     * read it before: otherwise the charge committed in between is invisible and the folio closes
     * with a balance.
     */
    @Test
    void shouldRefuseClosingWhenAChargeWasCommittedAfterTheSameTransactionReadTheFolio() {
        FolioId folioId = tabFolioOwing("100.00");
        folioService.receivePayment(folioId, PaymentMethod.CASH, Money.of("100.00"), "key-" + UUID.randomUUID());

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    assertThat(folioFacade.balanceOf(folioId)).isEqualTo(Money.ZERO);
                    postFromAnotherThread(folioId, "30.00");
                    folioFacade.close(folioId);
                }))
                .isInstanceOfSatisfying(DomainException.class, refused ->
                        assertThat(refused.code()).isEqualTo("FOLIO_BALANCE_NOT_ZERO"));

        FolioView folio = folioFacade.findById(folioId);
        assertThat(folio.status()).isEqualTo(FolioStatus.OPEN);
        assertThat(folio.balance()).isEqualTo(Money.of("30.00"));
    }

    // ------------------------------------------------------------- racing

    /** Posts and commits in a transaction of its own, while the caller's one stays open. */
    private void postFromAnotherThread(FolioId folioId, String amount) {
        try {
            executor.submit(() -> folioFacade.post(folioId, tabCharge(amount))).get(30, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Starts both tasks and releases them at the same instant. */
    private <T> List<T> runTogether(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<T> firstResult = executor.submit(released(first, ready, go));
        Future<T> secondResult = executor.submit(released(second, ready, go));
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        return List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
    }

    private static <T> Callable<T> released(Callable<T> task, CountDownLatch ready, CountDownLatch go) {
        return () -> {
            ready.countDown();
            go.await();
            return task.call();
        };
    }

    /** True when the write went through; false when the folio refused it with a code of its own. */
    private static boolean succeeds(Runnable write) {
        try {
            write.run();
            return true;
        } catch (DomainException refused) {
            assertThat(refused.code()).isIn("FOLIO_CLOSED", "FOLIO_BALANCE_NOT_ZERO");
            return false;
        }
    }

    // ------------------------------------------------------------- fixtures

    private FolioId tabFolioOwing(String amount) {
        FolioId folioId = folioFacade.openTabFolio(FolioOwner.tab(UUID.randomUUID()));
        folioFacade.post(folioId, tabCharge(amount));
        return folioId;
    }

    private static ChargeRequest tabCharge(String amount) {
        return new ChargeRequest(Money.of(amount), "Restaurant - tab", ChargeSource.tab(UUID.randomUUID()));
    }

    private HttpResponse<String> pay(String token, FolioId folioId, String key) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/billing/folios/" + folioId.value() + "/payments"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString("{\"method\":\"PIX\",\"amount\":\"50.00\"}"))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String idOf(HttpResponse<String> response) {
        JsonNode body = jsonMapper.readTree(response.body());
        return body.get("id").asString();
    }

    private String frontDeskToken() {
        String username = "folio_race_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(User.create(
                PROPERTY_ID, username, "Folio Race User", "folio-race-password", Set.of(Role.FRONT_DESK), passwordEncoder));
        return new JwtTokenIssuer(jwtSecret, clock).issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }
}
