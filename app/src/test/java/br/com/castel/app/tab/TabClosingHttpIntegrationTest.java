package br.com.castel.app.tab;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.billing.api.FolioFacade;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.FolioStatus;
import br.com.castel.identity.api.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Task 3.2 end to end: a tab taken from its first item to its close through the routes, with the
 * folio of billing behind it.
 *
 * <p>The arithmetic — the service charge over the sum, the largest remainder, the extra cent — and
 * what each status accepts are covered without a database in the unit tests of {@code restaurant}.
 * What this proves is the wiring: the setting read, the folio opened, charged, paid, reversed and
 * closed in the tab's transaction, the replay of a payment, and the table freed at the end.
 */
class TabClosingHttpIntegrationTest extends AbstractTabClosingIntegrationTest {

    @Autowired
    private FolioFacade folios;

    @Test
    void shouldCloseATabThroughThePreBillTheSplitAndTwoPayments() {
        String pizzaId = createItem("Pizza", "40.00", true);
        String sodaId = createItem("Soda", "6.00", true);
        String couvertId = createItem("Couvert", "12.00", false);
        String diningTableId = createDiningTable("Fatia");
        String tabId = openOnTable(diningTableId);
        String pizzaLine = order(tabId, pizzaId, 1);
        String sodaLine = order(tabId, sodaId, 2);
        String couvertLine = order(tabId, couvertId, 1);

        JsonNode waived = send(put(TABS + "/" + tabId + "/items/" + sodaLine + "/service-charge", waiterToken,
                "{\"applied\":false}"), 200);
        assertThat(waived.get("serviceCharge").asString()).isEqualTo("4.00");
        JsonNode split = send(put(TABS + "/" + tabId + "/split-groups", waiterToken, """
                {"assignments":[{"itemId":"%s","splitGroup":1},{"itemId":"%s","splitGroup":2},
                                {"itemId":"%s","splitGroup":2}]}
                """.formatted(pizzaLine, sodaLine, couvertLine)), 200);
        assertThat(split.get("groups").get(0).get("total").asString()).isEqualTo("44.00");
        assertThat(split.get("groups").get(1).get("total").asString()).isEqualTo("24.00");
        JsonNode bill = send(get(TABS + "/" + tabId + "/bill?parts=3", waiterToken), 200);
        assertThat(bill.get("total").asString()).isEqualTo("68.00");
        assertThat(bill.get("evenSplit").toString()).isEqualTo("[\"22.67\",\"22.67\",\"22.66\"]");

        JsonNode closing = send(post(TABS + "/" + tabId + "/closing", waiterToken, ""), 200);
        assertThat(closing.get("status").asString()).isEqualTo("CLOSING");
        assertThat(closing.get("balance").asString()).isEqualTo("68.00");
        JsonNode pix = send(pay(tabId, "PIX", "30.00", "closing-pix-" + suffix), 201);
        JsonNode replay = send(pay(tabId, "PIX", "30.00", "closing-pix-" + suffix), 201);
        assertThat(replay.get("paymentId").asString()).isEqualTo(pix.get("paymentId").asString());
        assertThat(replay.get("bill").get("balance").asString()).isEqualTo("38.00");
        send(pay(tabId, "CREDIT_CARD", "38.00", "closing-card-" + suffix), 201);

        JsonNode closed = send(post(TABS + "/" + tabId + "/close", waiterToken, ""), 200);

        assertThat(closed.get("status").asString()).isEqualTo("CLOSED");
        assertThat(closed.get("destination").asString()).isEqualTo("DIRECT_PAYMENT");
        assertThat(closed.get("total").asString()).isEqualTo("68.00");
        assertThat(folios.findById(FolioId.of(closed.get("folioId").asString())).status()).isEqualTo(FolioStatus.CLOSED);
        openOnTable(diningTableId);
    }

    @Test
    void shouldChargeTheNewTotalMinusWhatWasPaidAfterReopening() {
        String pizzaId = createItem("Pizza", "40.00", true);
        String puddingId = createItem("Pudim", "10.00", true);
        String tabId = openOnTable(createDiningTable("Reabre"));
        order(tabId, pizzaId, 1);
        send(post(TABS + "/" + tabId + "/closing", waiterToken, ""), 200);
        send(pay(tabId, "PIX", "10.00", "reopen-pix-" + suffix), 201);

        JsonNode reopened = send(post(TABS + "/" + tabId + "/reopen", waiterToken, "{\"reason\":\"sobremesa\"}"), 200);
        assertThat(reopened.get("status").asString()).isEqualTo("OPEN");
        order(tabId, puddingId, 1);
        JsonNode secondClosing = send(post(TABS + "/" + tabId + "/closing", waiterToken, ""), 200);

        assertThat(secondClosing.get("total").asString()).isEqualTo("55.00");
        assertThat(secondClosing.get("paid").asString()).isEqualTo("10.00");
        assertThat(secondClosing.get("balance").asString()).isEqualTo("45.00");
        JsonNode tab = send(get(TABS + "/" + tabId, waiterToken), 200);
        assertThat(tab.get("folioId").asString()).isEqualTo(reopened.get("folioId").asString());
        assertThat(folios.findById(FolioId.of(tab.get("folioId").asString())).charges()).hasSize(3);
    }

    @Test
    void shouldRefuseTheKitchenOnTheClosingRoutes() {
        String kitchenToken = accessTokenFor(createUser(Role.KITCHEN));
        String tabId = openOnTable(createDiningTable("Cozinha"));

        assertThat(send(post(TABS + "/" + tabId + "/closing", kitchenToken, ""), 403).get("code").asString())
                .isEqualTo("ACCESS_DENIED");
    }
}
