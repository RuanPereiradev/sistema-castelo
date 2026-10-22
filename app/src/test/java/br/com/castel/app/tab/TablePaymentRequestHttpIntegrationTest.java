package br.com.castel.app.tab;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Task qr-table-payment end to end: the phone of the table reads its tab and pays chosen items by Pix,
 * the waiter confirms, rejects or refunds, and the folio of the tab follows.
 *
 * <p>The money rules — the frozen amount of a part, the swap of the closing charge, what a held item
 * refuses — are covered without a database in {@code TabPaymentPartTest}. What this proves is the
 * wiring, the roles, the lock that queues two phones on the same item, and the codes, one case per
 * rule.
 *
 * <p>There is no route that removes the Pix receiver, and the settings are cached in the context, so
 * the case without a receiver runs first, on a database where no other class writes one.
 *
 * <p>Every tab orders 100.00 + 33.35; with the 10% the test property is seeded with, the 33.35 costs
 * 33.35 + 3.34 = 36.69 from the phone, and 110.00 is left.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TablePaymentRequestHttpIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "pix-test-password";
    private static final String TABS = "/api/restaurant/tabs";
    private static final String PAYMENTS = "/api/restaurant/table-payment-requests";
    private static final String PIX_RECEIVER = "/api/admin/pix-receiver";
    private static final int CONCURRENT_PHONES = 5;
    private static final String DEVICE_HEADER = "X-Customer-Device";
    private static final String CUSTOMER_NAME = "Ana";
    private static final String DEVICE_ID = "phone-of-ana-0001";
    private static final String OTHER_DEVICE_ID = "phone-of-bruno-02";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    private String adminToken;
    private String waiterToken;
    private String suffix;

    /** The table of the test, with its tab open and two items on it. */
    private String orderingToken;
    private String tabId;
    private String hundredId;
    private String smallId;

    @BeforeEach
    void arrange() {
        adminToken = accessTokenFor(createUser(Role.ADMIN));
        waiterToken = accessTokenFor(createUser(Role.WAITER));
        suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode table = send(post("/api/restaurant/dining-tables", adminToken,
                "{\"label\":\"Pix %s\"}".formatted(suffix)), 201);
        orderingToken = table.get("orderingToken").asString();
        tabId = send(post(TABS, waiterToken, "{\"origin\":\"TABLE_SERVICE\",\"diningTableId\":\"%s\"}"
                .formatted(table.get("id").asString())), 201).get("id").asString();
        hundredId = order(createItem("Prato " + suffix, "100.00"));
        smallId = order(createItem("Suco " + suffix, "33.35"));
    }

    @Test
    @Order(1)
    void shouldRefuseThePaymentWhenNoPixReceiverIsConfigured() {
        jdbcTemplate.update("delete from setting where setting_key like 'pix.%'");

        assertThat(send(publicGet("/tab"), 200).get("acceptsPayment").asBoolean()).isFalse();
        assertCode(publicPost("/payment-requests", itemsBody(smallId)), 409, "PIX_RECEIVER_NOT_CONFIGURED");
    }

    @Test
    void shouldMarkTheItemPaidAndLeaveTheFolioAtZeroWhenTheWaiterConfirmsWhileTheTabIsOpen() {
        configurePix();
        JsonNode request = send(publicPost("/payment-requests", itemsBody(smallId)), 201);
        assertThat(request.get("amount").asString()).isEqualTo("36.69");
        assertThat(request.get("pixPayload").asString()).contains("540536.69");

        send(post(PAYMENTS + "/" + request.get("id").asString() + "/confirm", waiterToken, ""), 200);

        JsonNode tab = send(get(TABS + "/" + tabId, waiterToken), 200);
        assertThat(itemOf(tab, smallId).get("paid").asBoolean()).isTrue();
        assertThat(tab.get("paidAmount").asString()).isEqualTo("36.69");
        assertThat(tab.get("amountDue").asString()).isEqualTo("110.00");
        JsonNode folio = send(get("/api/billing/folios/" + tab.get("folioId").asString(), adminToken), 200);
        assertThat(folio.get("balance").asString()).isEqualTo("0.00");
        assertThat(send(publicGet("/payment-requests/" + request.get("id").asString()), 200)
                .get("status").asString()).isEqualTo("CONFIRMED");
    }

    @Test
    void shouldReserveTheItemForOnlyOneOfFivePhonesPickingItAtOnce() throws Exception {
        configurePix();
        List<Callable<HttpResponse<String>>> phones = new ArrayList<>();
        for (int phone = 0; phone < CONCURRENT_PHONES; phone++) {
            phones.add(() -> exchange(publicPost("/payment-requests", itemsBody(smallId))));
        }

        List<HttpResponse<String>> responses = concurrently(phones);

        assertThat(responses).filteredOn(response -> response.statusCode() == 201).hasSize(1);
        for (HttpResponse<String> response : responses.stream().filter(r -> r.statusCode() != 201).toList()) {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
            assertThat(jsonMapper.readTree(response.body()).get("code").asString())
                    .isEqualTo("TAB_ITEM_RESERVED_FOR_PAYMENT");
        }
        String reservedBy = StreamSupport.stream(send(get(PAYMENTS, waiterToken), 200).spliterator(), false)
                .filter(pending -> pending.get("tabId").asString().equals(tabId))
                .map(pending -> pending.get("id").asString())
                .reduce((first, second) -> first + "," + second)
                .orElseThrow();
        assertThat(reservedBy).doesNotContain(",");
    }

    @Test
    void shouldFreeTheItemsWhenTheWaiterRejects() {
        configurePix();
        String requestId = send(publicPost("/payment-requests", itemsBody(smallId)), 201).get("id").asString();

        send(post(PAYMENTS + "/" + requestId + "/reject", waiterToken, ""), 200);

        JsonNode item = itemOf(send(publicGet("/tab"), 200), smallId);
        assertThat(item.get("reservedQuantity").asInt()).isZero();
        assertThat(item.get("paid").asBoolean()).isFalse();
        assertThat(send(publicGet("/payment-requests/" + requestId), 200).get("status").asString())
                .isEqualTo("REJECTED");
        send(publicPost("/payment-requests", itemsBody(smallId)), 201);
    }

    @Test
    void shouldConfirmWhileTheTabIsClosingAndCloseOnceEverythingIsPaid() {
        configurePix();
        send(post(TABS + "/" + tabId + "/closing", waiterToken, ""), 200);
        String folioPath = "/api/billing/folios/" + send(get(TABS + "/" + tabId, waiterToken), 200)
                .get("folioId").asString();
        assertThat(send(get(folioPath, adminToken), 200).get("balance").asString()).isEqualTo("146.69");

        confirm(send(publicPost("/payment-requests", itemsBody(smallId)), 201));
        assertThat(send(get(folioPath, adminToken), 200).get("balance").asString()).isEqualTo("110.00");
        confirm(send(publicPost("/payment-requests", itemsBody(hundredId)), 201));
        assertThat(send(get(folioPath, adminToken), 200).get("balance").asString()).isEqualTo("0.00");

        JsonNode closed = send(post(TABS + "/" + tabId + "/close", waiterToken, ""), 200);
        assertThat(closed.get("status").asString()).isEqualTo("CLOSED");
        assertThat(closed.get("amountDue").asString()).isEqualTo("0.00");
    }

    @Test
    void shouldLetTheWaiterCancelAPaidItemOnlyAfterRefundingIt() {
        configurePix();
        String requestId = confirm(send(publicPost("/payment-requests", itemsBody(smallId)), 201));
        String cancelPath = TABS + "/" + tabId + "/items/" + smallId + "/cancel";
        assertCode(post(cancelPath, waiterToken, "{\"reason\":\"voltou\"}"), 409, "TAB_ITEM_ALREADY_PAID");

        send(post(PAYMENTS + "/" + requestId + "/refund", waiterToken, "{\"reason\":\"comprovante falso\"}"), 200);

        JsonNode afterCancel = send(post(cancelPath, waiterToken, "{\"reason\":\"voltou\"}"), 200);
        assertThat(itemOf(afterCancel, smallId).get("status").asString()).isEqualTo("CANCELLED");
        assertThat(afterCancel.get("paidAmount").asString()).isEqualTo("0.00");
    }

    @Test
    void shouldShowThePhonePaidReservedAndDueWithoutAnyUserId() {
        configurePix();
        confirm(send(publicPost("/payment-requests", itemsBody(smallId)), 201));
        send(publicPost("/payment-requests", itemsBody(hundredId)), 201);
        String openedBy = send(get(TABS + "/" + tabId, waiterToken), 200).get("openedBy").asString();

        HttpResponse<String> response = exchange(publicGet("/tab"));

        JsonNode tab = jsonMapper.readTree(response.body());
        assertThat(itemOf(tab, smallId).get("paid").asBoolean()).isTrue();
        assertThat(itemOf(tab, hundredId).get("reservedQuantity").asInt()).isEqualTo(1);
        assertThat(tab.get("total").asString()).isEqualTo("146.69");
        assertThat(tab.get("paidAmount").asString()).isEqualTo("36.69");
        assertThat(tab.get("amountDue").asString()).isEqualTo("110.00");
        assertThat(tab.get("acceptsPayment").asBoolean()).isTrue();
        assertThat(response.body()).doesNotContain(openedBy).doesNotContain("orderedBy").doesNotContain("openedBy");
    }

    @Test
    void shouldLetOnlyTheAdminWriteThePixReceiver() {
        String body = "{\"key\":\"x@castel.com.br\",\"receiverName\":\"Castel\",\"city\":\"Fortaleza\"}";

        assertCode(put(PIX_RECEIVER, accessTokenFor(createUser(Role.FRONT_DESK)), body), 403, "ACCESS_DENIED");
        assertCode(put(PIX_RECEIVER, waiterToken, body), 403, "ACCESS_DENIED");
    }

    /** 3 × 3.35 joins the tab: 143.40. Unpaid 136.70 owes 13.67 of service: 157.07, of which 6.70 paid. */
    @Test
    void shouldPayTwoOfThreeUnitsWithoutServiceAndLeaveTheLastUnitDue() {
        configurePix();
        String trioId = order(createItem("Pastel " + suffix, "3.35"), 3);

        JsonNode request = send(publicPost("/payment-requests", paymentBody(CUSTOMER_NAME, "false",
                unitsOf(trioId, 2))), 201);
        assertThat(request.get("amount").asString()).isEqualTo("6.70");
        assertThat(request.get("serviceCharge").asString()).isEqualTo("0.00");
        assertThat(request.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
        confirm(request);

        JsonNode tab = send(get(TABS + "/" + tabId, waiterToken), 200);
        JsonNode trio = itemOf(tab, trioId);
        assertThat(trio.get("paidQuantity").asInt()).isEqualTo(2);
        assertThat(trio.get("paid").asBoolean()).isFalse();
        assertThat(tab.get("total").asString()).isEqualTo("157.07");
        assertThat(tab.get("paidAmount").asString()).isEqualTo("6.70");
        assertThat(tab.get("amountDue").asString()).isEqualTo("150.37");
    }

    @Test
    void shouldRefuseAPaymentThatDoesNotSayWhetherItIncludesTheServiceCharge() {
        configurePix();

        assertCode(publicPost("/payment-requests", paymentBody(CUSTOMER_NAME, null, unitsOf(smallId, 1))), 400,
                "VALIDATION_FAILED");
    }

    @Test
    void shouldRefuseAPaymentWithoutTheNameOfTheCustomer() {
        configurePix();

        assertCode(publicPost("/payment-requests", paymentBody(null, "true", unitsOf(smallId, 1))), 422,
                "INVALID_CUSTOMER_NAME");
    }

    @Test
    void shouldRefuseMoreUnitsThanTheLineHas() {
        configurePix();

        assertCode(publicPost("/payment-requests", paymentBody(CUSTOMER_NAME, "true", unitsOf(smallId, 2))), 422,
                "INVALID_TABLE_PAYMENT_REQUEST");
    }

    @Test
    void shouldTellOnlyTheSendingDeviceWhichItemsAreItsOwnWithoutReturningAnyDeviceCode() {
        String fromPhone = orderFromThePhone(createItem("Cocada " + suffix, "8.00"));

        HttpResponse<String> own = exchange(publicGet("/tab").header(DEVICE_HEADER, DEVICE_ID));
        HttpResponse<String> other = exchange(publicGet("/tab").header(DEVICE_HEADER, OTHER_DEVICE_ID));
        HttpResponse<String> anonymous = exchange(publicGet("/tab"));

        JsonNode ownTab = jsonMapper.readTree(own.body());
        assertThat(itemOf(ownTab, fromPhone).get("mine").asBoolean()).isTrue();
        assertThat(itemOf(ownTab, fromPhone).get("customerName").asString()).isEqualTo(CUSTOMER_NAME);
        assertThat(itemOf(ownTab, hundredId).get("mine").asBoolean()).isFalse();
        assertThat(itemOf(jsonMapper.readTree(other.body()), fromPhone).get("mine").asBoolean()).isFalse();
        assertThat(itemOf(jsonMapper.readTree(anonymous.body()), fromPhone).get("mine").asBoolean()).isFalse();
        assertThat(own.body()).doesNotContain(DEVICE_ID);
        assertThat(other.body()).doesNotContain(DEVICE_ID).doesNotContain(OTHER_DEVICE_ID);
    }

    /** Same rule as the order request (decision #34): the job wrote EXPIRED before the waiter answered. */
    @Test
    void shouldAnswerExpiredWhenTheWaiterAnswersARequestTheJobAlreadyExpired() {
        configurePix();
        String requestId = send(publicPost("/payment-requests", itemsBody(smallId)), 201).get("id").asString();
        storeAsExpiredByTheJob(requestId, smallId);

        assertCode(post(PAYMENTS + "/" + requestId + "/confirm", waiterToken, ""), 409,
                "TABLE_PAYMENT_REQUEST_EXPIRED");
        assertCode(post(PAYMENTS + "/" + requestId + "/reject", waiterToken, ""), 409,
                "TABLE_PAYMENT_REQUEST_EXPIRED");
        assertThat(send(publicGet("/payment-requests/" + requestId), 200).get("status").asString())
                .isEqualTo("EXPIRED");
    }

    /** Decision #49: the counter refunded the Pix first; the restaurant route still undoes the part. */
    @Test
    void shouldUndoThePartWhenThePixWasAlreadyRefundedAtTheCounter() {
        configurePix();
        String requestId = confirm(send(publicPost("/payment-requests", itemsBody(smallId)), 201));
        String folioPath = "/api/billing/folios/" + send(get(TABS + "/" + tabId, waiterToken), 200)
                .get("folioId").asString();
        String paymentId = send(get(folioPath, adminToken), 200).get("payments").get(0).get("id").asString();
        send(post(folioPath + "/payments/" + paymentId + "/refund", adminToken, "{\"reason\":\"balcao\"}"), 200);

        JsonNode refunded = send(post(PAYMENTS + "/" + requestId + "/refund", waiterToken,
                "{\"reason\":\"comprovante falso\"}"), 200);

        assertThat(refunded.get("refunded").asBoolean()).isTrue();
        assertThat(itemOf(send(publicGet("/tab"), 200), smallId).get("paidQuantity").asInt()).isZero();
        assertThat(send(get(folioPath, adminToken), 200).get("balance").asString()).isEqualTo("0.00");
    }

    /** Decision #51: a table whose tab is gone answers like a table without one, never with the tab id. */
    @Test
    void shouldAnswerTabNotFoundWithoutTheTabIdOnceTheTabOfTheTableWasMerged() {
        configurePix();
        JsonNode otherTable = send(post("/api/restaurant/dining-tables", adminToken,
                "{\"label\":\"Pix B %s\"}".formatted(suffix)), 201);
        String otherTabId = send(post(TABS, waiterToken, "{\"origin\":\"TABLE_SERVICE\",\"diningTableId\":\"%s\"}"
                .formatted(otherTable.get("id").asString())), 201).get("id").asString();
        send(post(TABS + "/" + otherTabId + "/merge", waiterToken, "{\"mergedTabId\":\"%s\"}".formatted(tabId)), 200);

        HttpResponse<String> response = exchange(publicPost("/payment-requests", itemsBody(smallId)));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
        assertThat(jsonMapper.readTree(response.body()).get("code").asString()).isEqualTo("TAB_NOT_FOUND");
        assertThat(response.body()).doesNotContain(tabId).doesNotContain(otherTabId);
    }

    /** Decision #52: a Pix past its window no longer holds the closing; closing expires it first. */
    @Test
    void shouldExpireTheOverduePaymentOfTheTabAndThenCloseIt() {
        configurePix();
        String requestId = send(publicPost("/payment-requests", itemsBody(smallId)), 201).get("id").asString();
        ageThirtyOneMinutes(requestId);
        send(post(TABS + "/" + tabId + "/closing", waiterToken, ""), 200);
        send(post(TABS + "/" + tabId + "/payments", waiterToken, "{\"method\":\"CASH\",\"amount\":\"146.69\"}")
                .header("Idempotency-Key", "cash-" + suffix), 201);

        JsonNode closed = send(post(TABS + "/" + tabId + "/close", waiterToken, ""), 200);

        assertThat(closed.get("status").asString()).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject("select status from table_payment_request where id = ?",
                String.class, UUID.fromString(requestId))).isEqualTo("EXPIRED");
    }

    /** Decisions #55–#58: the same amount as the real request, no Pix, nothing reserved, no customer. */
    @Test
    void shouldQuoteTheAmountOfTheRealRequestWithoutReservingAnythingOrAskingTheCustomer() {
        configurePix();
        String trioId = order(createItem("Pastel " + suffix, "3.35"), 3);
        String units = unitsOf(trioId, 2) + "," + unitsOf(smallId, 1);

        JsonNode quote = send(publicPost("/payment-requests/quote", quoteBody("true", units)), 200);

        assertThat(quote.get("amount").asString()).isEqualTo("44.06");
        assertThat(quote.has("pixPayload")).isFalse();
        JsonNode tab = send(publicGet("/tab"), 200);
        assertThat(itemOf(tab, trioId).get("reservedQuantity").asInt()).isZero();
        assertThat(itemOf(tab, smallId).get("reservedQuantity").asInt()).isZero();
        JsonNode request = send(publicPost("/payment-requests", paymentBody(CUSTOMER_NAME, "true",
                unitsOf(trioId, 2), unitsOf(smallId, 1))), 201);
        assertThat(request.get("amount").asString()).isEqualTo(quote.get("amount").asString());
        assertThat(request.get("serviceCharge").asString()).isEqualTo(quote.get("serviceCharge").asString());
    }

    @Test
    void shouldRefuseAQuoteOfMoreUnitsThanTheLineHas() {
        configurePix();

        assertCode(publicPost("/payment-requests/quote", quoteBody("true", unitsOf(smallId, 2))), 422,
                "INVALID_TABLE_PAYMENT_REQUEST");
    }

    @Test
    void shouldRefuseAQuoteOfAnItemAlreadyPaid() {
        configurePix();
        confirm(send(publicPost("/payment-requests", itemsBody(smallId)), 201));

        assertCode(publicPost("/payment-requests/quote", quoteBody("true", unitsOf(smallId, 1))), 409,
                "TAB_ITEM_ALREADY_PAID");
    }

    @Test
    void shouldAnswerTabNotFoundToAQuoteFromATableWithoutATab() {
        configurePix();
        String emptyTableToken = send(post("/api/restaurant/dining-tables", adminToken,
                "{\"label\":\"Pix vazia %s\"}".formatted(suffix)), 201).get("orderingToken").asString();

        assertCode(anonymous("/public/tables/" + emptyTableToken + "/payment-requests/quote")
                .POST(HttpRequest.BodyPublishers.ofString(quoteBody("true", unitsOf(smallId, 1)))), 404,
                "TAB_NOT_FOUND");
    }

    // ------------------------------------------------------------- confirmed payments of a tab (#59–#61)

    /** Requested hundred then small, confirmed small then hundred: the list follows the confirmation. */
    @Test
    void shouldListTheConfirmedPaymentsOfTheTabOldestConfirmationFirstWithTheRefundedOne() {
        configurePix();
        JsonNode hundred = send(publicPost("/payment-requests", itemsBody(hundredId)), 201);
        JsonNode small = send(publicPost("/payment-requests", itemsBody(smallId)), 201);
        confirm(small);
        confirm(hundred);
        String smallRequestId = small.get("id").asString();
        send(post(PAYMENTS + "/" + smallRequestId + "/refund", waiterToken, "{\"reason\":\"comprovante falso\"}"),
                200);

        JsonNode confirmed = send(get(confirmedOf(tabId), waiterToken), 200);

        assertThat(ids(confirmed)).containsExactly(smallRequestId, hundred.get("id").asString());
        JsonNode refunded = confirmed.get(0);
        assertThat(refunded.get("refunded").asBoolean()).isTrue();
        assertThat(refunded.get("refundReason").asString()).isEqualTo("comprovante falso");
        assertThat(confirmed.get(1).get("refunded").asBoolean()).isFalse();
    }

    @Test
    void shouldLeavePendingPaymentsOutOfTheConfirmedList() {
        configurePix();
        confirm(send(publicPost("/payment-requests", itemsBody(smallId)), 201));
        String pendingId = send(publicPost("/payment-requests", itemsBody(hundredId)), 201).get("id").asString();

        assertThat(ids(send(get(confirmedOf(tabId), waiterToken), 200))).hasSize(1).doesNotContain(pendingId);
    }

    @Test
    void shouldLeaveTheConfirmedPaymentsOfAnotherTabOut() {
        configurePix();
        String ownId = confirm(send(publicPost("/payment-requests", itemsBody(smallId)), 201));
        String ownTabId = tabId;
        arrange();
        String otherId = confirm(send(publicPost("/payment-requests", itemsBody(smallId)), 201));

        assertThat(ids(send(get(confirmedOf(ownTabId), waiterToken), 200))).containsExactly(ownId)
                .doesNotContain(otherId);
    }

    @Test
    void shouldRefuseAStatusOtherThanConfirmed() {
        assertCode(get(PAYMENTS + "?status=PENDING&tabId=" + tabId, waiterToken), 400, "VALIDATION_FAILED");
    }

    @Test
    void shouldRefuseTheConfirmedListWithoutATab() {
        assertCode(get(PAYMENTS + "?status=CONFIRMED", waiterToken), 400, "VALIDATION_FAILED");
    }

    @Test
    void shouldAnswerAnEmptyListForAnUnknownTab() {
        assertThat(send(get(confirmedOf(UUID.randomUUID().toString()), waiterToken), 200)).isEmpty();
    }

    @Test
    void shouldNotLetTheFrontDeskReadTheConfirmedPayments() {
        assertCode(get(confirmedOf(tabId), accessTokenFor(createUser(Role.FRONT_DESK))), 403, "ACCESS_DENIED");
    }

    @Test
    void shouldKeepAnsweringThePendingQueueWithoutParameters() {
        configurePix();
        String confirmedId = confirm(send(publicPost("/payment-requests", itemsBody(smallId)), 201));
        String pendingId = send(publicPost("/payment-requests", itemsBody(hundredId)), 201).get("id").asString();

        assertThat(ids(send(get(PAYMENTS, waiterToken), 200))).contains(pendingId).doesNotContain(confirmedId);
    }

    // ------------------------------------------------------------- fixtures

    private static String confirmedOf(String tabId) {
        return PAYMENTS + "?status=CONFIRMED&tabId=" + tabId;
    }

    private static List<String> ids(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(node -> node.get("id").asString()).toList();
    }

    private static String quoteBody(String includeServiceCharge, String items) {
        return "{\"includeServiceCharge\":%s,\"items\":[%s]}".formatted(includeServiceCharge, items);
    }

    /** Ages a pending request past its window, as the clock would, leaving it stored PENDING. */
    private void ageThirtyOneMinutes(String requestId) {
        int updated = jdbcTemplate.update("update table_payment_request "
                + "set requested_at = requested_at - interval '31 minutes', "
                + "expires_at = expires_at - interval '31 minutes' where id = ?", UUID.fromString(requestId));
        assertThat(updated).isEqualTo(1);
    }

    /** What the expiry job leaves behind: the request aged, stored EXPIRED, its unit free. */
    private void storeAsExpiredByTheJob(String requestId, String tabItemId) {
        int requests = jdbcTemplate.update("update table_payment_request set status = 'EXPIRED', "
                + "requested_at = requested_at - interval '31 minutes', "
                + "expires_at = expires_at - interval '31 minutes', resolved_at = now() where id = ?",
                UUID.fromString(requestId));
        int items = jdbcTemplate.update("update tab_item set reserved_quantity = 0 where id = ?",
                UUID.fromString(tabItemId));
        assertThat(requests + items).isEqualTo(2);
    }

    /** Orders one unit from the phone of Ana, the waiter confirms, and answers the new line on the tab. */
    private String orderFromThePhone(String menuItemId) {
        String body = """
                {"customerName":"%s","customerDeviceId":"%s","lines":[{"menuItemId":"%s","quantity":1,"modifiers":[]}]}
                """.formatted(CUSTOMER_NAME, DEVICE_ID, menuItemId);
        String requestId = send(publicPost("/order-requests", body), 201).get("id").asString();
        send(post("/api/restaurant/table-order-requests/" + requestId + "/confirm", waiterToken, ""), 200);
        JsonNode items = send(get(TABS + "/" + tabId, waiterToken), 200).get("items");
        return items.get(items.size() - 1).get("id").asString();
    }

    private void configurePix() {
        send(put(PIX_RECEIVER, adminToken,
                "{\"key\":\"pix@castel.com.br\",\"receiverName\":\"Castel Restaurante\",\"city\":\"Fortaleza\"}"), 200);
    }

    private String confirm(JsonNode request) {
        String requestId = request.get("id").asString();
        send(post(PAYMENTS + "/" + requestId + "/confirm", waiterToken, ""), 200);
        return requestId;
    }

    /** One unit of each item, with the service charge, by Ana. */
    private static String itemsBody(String... tabItemIds) {
        return paymentBody(CUSTOMER_NAME, "true", Arrays.stream(tabItemIds).map(id -> unitsOf(id, 1))
                .toArray(String[]::new));
    }

    private static String paymentBody(String customerName, String includeServiceCharge, String... items) {
        String name = customerName == null ? "null" : "\"" + customerName + "\"";
        String service = includeServiceCharge == null ? "" : ",\"includeServiceCharge\":" + includeServiceCharge;
        return "{\"customerName\":%s,\"customerDeviceId\":\"%s\"%s,\"items\":[%s]}"
                .formatted(name, DEVICE_ID, service, String.join(",", items));
    }

    private static String unitsOf(String tabItemId, int quantity) {
        return "{\"tabItemId\":\"%s\",\"quantity\":%d}".formatted(tabItemId, quantity);
    }

    private String createItem(String name, String price) {
        String categoryId = send(post("/api/restaurant/menu-categories", adminToken,
                        "{\"name\":\"Pix %s\",\"displayOrder\":1}".formatted(UUID.randomUUID())), 201)
                .get("id").asString();
        return send(post("/api/restaurant/menu-items", adminToken, """
                {"categoryId":"%s","name":"%s","prepStation":"KITCHEN","soldByWeight":false,"price":"%s"}
                """.formatted(categoryId, name, price)), 201).get("id").asString();
    }

    /** Orders one unit and answers the id of the line on the tab. */
    private String order(String menuItemId) {
        return order(menuItemId, 1);
    }

    private String order(String menuItemId, int quantity) {
        JsonNode tab = send(post(TABS + "/" + tabId + "/items", waiterToken,
                "{\"menuItemId\":\"%s\",\"quantity\":%d}".formatted(menuItemId, quantity)), 201);
        JsonNode items = tab.get("items");
        return items.get(items.size() - 1).get("id").asString();
    }

    // ------------------------------------------------------------- reading the answers

    private static JsonNode itemOf(JsonNode tab, String itemId) {
        return StreamSupport.stream(tab.get("items").spliterator(), false)
                .filter(item -> item.get("id").asString().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No item " + itemId + " in " + tab));
    }

    private void assertCode(HttpRequest.Builder builder, int expectedStatus, String code) {
        assertThat(send(builder, expectedStatus).get("code").asString()).isEqualTo(code);
    }

    /** Runs every task at the same moment, released together by one latch, and waits for all. */
    private static <T> List<T> concurrently(List<Callable<T>> tasks) throws Exception {
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

    // ------------------------------------------------------------- http helpers

    private HttpRequest.Builder publicGet(String path) {
        return anonymous("/public/tables/" + orderingToken + path).GET();
    }

    private HttpRequest.Builder publicPost(String path, String body) {
        return anonymous("/public/tables/" + orderingToken + path).POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder anonymous(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
    }

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
        return anonymous(path).header("Authorization", "Bearer " + token);
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

    // ------------------------------------------------------------- identity fixtures

    private String createUser(Role role) {
        String username = "pix_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(User.create(PROPERTY_ID, username, "Pix Test User", PASSWORD, Set.of(role), passwordEncoder));
        return username;
    }

    private String accessTokenFor(String username) {
        return new JwtTokenIssuer(jwtSecret, clock)
                .issueAccessToken(userRepository.findByUsername(username).orElseThrow());
    }
}
