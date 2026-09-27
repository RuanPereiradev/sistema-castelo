package br.com.castel.app.tab;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.billing.api.ChargeView;
import br.com.castel.billing.api.FolioFacade;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.FolioView;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * The closing of a tab under real concurrent requests (task 3.2), where the database decides.
 *
 * <p>Changing the status of a tab takes it {@code FOR UPDATE}; ordering, payments and the split take
 * it {@code FOR KEY SHARE}. These tests prove what that buys: the charge posted is always the total of
 * the items committed; one closing wins; a tab is never cancelled with an active item (inherited from
 * task 2.2); and a reopening racing a payment leaves the folio coherent, never a 500.
 */
class TabClosingConcurrencyIntegrationTest extends AbstractTabClosingIntegrationTest {

    private static final int CONCURRENT_ORDERS = 5;
    private static final int CONCURRENT_CLOSINGS = 3;
    private static final int ROUNDS = 5;

    @Autowired
    private FolioFacade folios;

    @Test
    void shouldPostTheTotalOfTheCommittedItemsWhenOrdersRaceTheStartOfClosing() throws Exception {
        String sodaId = createItem("Soda", "6.00", true);
        String tabId = openOnTable(createDiningTable("Corrida"));
        order(tabId, sodaId, 1);
        String order = "{\"menuItemId\":\"%s\"}".formatted(sodaId);
        List<Callable<HttpResponse<String>>> requests = new ArrayList<>();
        for (int attempt = 0; attempt < CONCURRENT_ORDERS; attempt++) {
            requests.add(() -> exchange(post(TABS + "/" + tabId + "/items", waiterToken, order)));
        }
        requests.add(() -> exchange(post(TABS + "/" + tabId + "/closing", waiterToken, "")));

        List<HttpResponse<String>> responses = concurrently(requests);

        assertThat(responses.get(CONCURRENT_ORDERS).statusCode()).as(responses.get(CONCURRENT_ORDERS).body())
                .isEqualTo(200);
        for (HttpResponse<String> response : responses.subList(0, CONCURRENT_ORDERS)) {
            if (response.statusCode() != 201) {
                assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
                assertThat(codeOf(response)).isEqualTo("TAB_NOT_OPEN");
            }
        }
        JsonNode tab = send(get(TABS + "/" + tabId, waiterToken), 200);
        long accepted = responses.subList(0, CONCURRENT_ORDERS).stream()
                .filter(response -> response.statusCode() == 201).count();
        assertThat(tab.get("items")).hasSize((int) accepted + 1);
        FolioView folio = folios.findById(FolioId.of(tab.get("folioId").asString()));
        assertThat(folio.charges()).hasSize(1);
        assertThat(folio.charges().get(0).amount().asString()).isEqualTo(tab.get("total").asString());
    }

    @Test
    void shouldStartClosingOnceWhenThreeRequestsStartItAtOnce() throws Exception {
        String sodaId = createItem("Soda", "6.00", true);
        String tabId = openOnTable(createDiningTable("Tres"));
        order(tabId, sodaId, 1);
        List<Callable<HttpResponse<String>>> requests = new ArrayList<>();
        for (int attempt = 0; attempt < CONCURRENT_CLOSINGS; attempt++) {
            requests.add(() -> exchange(post(TABS + "/" + tabId + "/closing", waiterToken, "")));
        }

        List<HttpResponse<String>> responses = concurrently(requests);

        assertThat(responses).filteredOn(response -> response.statusCode() == 200).hasSize(1);
        for (HttpResponse<String> response : responses) {
            if (response.statusCode() != 200) {
                assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
                assertThat(codeOf(response)).isEqualTo("TAB_NOT_OPEN");
            }
        }
        JsonNode tab = send(get(TABS + "/" + tabId, waiterToken), 200);
        assertThat(folios.findById(FolioId.of(tab.get("folioId").asString())).charges()).hasSize(1);
    }

    /** Inherited from task 2.2 (decision #18): the cancellation waits for the ordering, or refuses it. */
    @Test
    void shouldNeverCancelATabWithAnActiveItemWhenAnOrderRacesTheCancellation() throws Exception {
        String sodaId = createItem("Soda", "6.00", true);
        for (int round = 0; round < ROUNDS; round++) {
            String tabId = openOnTable(createDiningTable("Cancela " + round));
            String order = "{\"menuItemId\":\"%s\"}".formatted(sodaId);

            List<HttpResponse<String>> responses = concurrently(List.of(
                    () -> exchange(post(TABS + "/" + tabId + "/items", waiterToken, order)),
                    () -> exchange(post(TABS + "/" + tabId + "/cancel", waiterToken, "{\"reason\":\"engano\"}"))));

            JsonNode tab = send(get(TABS + "/" + tabId, waiterToken), 200);
            boolean hasActiveItem = tab.get("items").size() > 0;
            if (tab.get("status").asString().equals("CANCELLED")) {
                assertThat(hasActiveItem).as("a cancelled tab has no active item").isFalse();
                assertThat(codeOf(responses.get(0))).isEqualTo("TAB_NOT_OPEN");
            } else {
                assertThat(hasActiveItem).isTrue();
                assertThat(codeOf(responses.get(1))).isEqualTo("TAB_HAS_ACTIVE_ITEMS");
            }
        }
    }

    @Test
    void shouldKeepTheFolioCoherentWhenAReopeningRacesAPayment() throws Exception {
        String sodaId = createItem("Soda", "6.00", true);
        for (int round = 0; round < ROUNDS; round++) {
            String tabId = openOnTable(createDiningTable("Reabre " + round));
            order(tabId, sodaId, 5);
            send(post(TABS + "/" + tabId + "/closing", waiterToken, ""), 200);
            String folioId = send(get(TABS + "/" + tabId, waiterToken), 200).get("folioId").asString();
            String key = "race-" + suffix + "-" + round;

            List<HttpResponse<String>> responses = concurrently(List.of(
                    () -> exchange(pay(tabId, "PIX", "10.00", key)),
                    () -> exchange(post(TABS + "/" + tabId + "/reopen", waiterToken, "{\"reason\":\"corrida\"}"))));

            HttpResponse<String> payment = responses.get(0);
            assertThat(responses.get(1).statusCode()).as(responses.get(1).body()).isEqualTo(200);
            FolioView folio = folios.findById(FolioId.of(folioId));
            assertThat(folio.charges()).extracting(ChargeView::isReversal).containsExactly(false, true);
            if (payment.statusCode() == 201) {
                assertThat(folio.balance().asString()).isEqualTo("-10.00");
            } else {
                assertThat(payment.statusCode()).as(payment.body()).isEqualTo(409);
                assertThat(codeOf(payment)).isEqualTo("TAB_NOT_CLOSING");
                assertThat(folio.balance().asString()).isEqualTo("0.00");
            }
            assertThat(send(get(TABS + "/" + tabId, waiterToken), 200).get("status").asString()).isEqualTo("OPEN");
        }
    }
}
