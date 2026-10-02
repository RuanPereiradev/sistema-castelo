package br.com.castel.app.tab;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.identity.api.Role;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Moving items between tabs through the real routes (task 3.6).
 *
 * <p>What is proved here and nowhere else: the column {@code tab_item.tab_id} actually changes in the
 * database, since the aggregate alone would pass with the line only moved in memory; the trail rows
 * of {@code tab_item_transfer} are written with the kind of each operation; the reception reaches
 * these routes and the kitchen does not; and the kitchen goes on advancing an item that was
 * transferred while it was being prepared, which is the limitation task 3.5 left behind.
 */
class TabTransferHttpIntegrationTest extends AbstractTabClosingIntegrationTest {

    private static final String KITCHEN_ITEMS = "/api/kitchen/items/";

    @Autowired
    private DataSource dataSource;

    private String frontDeskToken;
    private String kitchenToken;

    @BeforeEach
    void logInTheOtherTwoRoles() {
        frontDeskToken = accessTokenFor(createUser(Role.FRONT_DESK));
        kitchenToken = accessTokenFor(createUser(Role.KITCHEN));
    }

    @Test
    void shouldMoveTheLineToTheOtherTabAndWriteItsNewTabOnTheRow() {
        String sodaId = createItem("Soda", "6.00", true);
        String source = openOnTable(createDiningTable("Origem"));
        String destination = openOnTable(createDiningTable("Destino"));
        String itemId = order(source, sodaId, 2);

        JsonNode response = send(transfer(source, destination, itemId), 200);

        assertThat(response.get("movedItems").asInt()).isEqualTo(1);
        assertThat(response.get("source").get("subtotal").asString()).isEqualTo("0.00");
        assertThat(response.get("destination").get("subtotal").asString()).isEqualTo("12.00");
        assertThat(tabIdOf(itemId)).isEqualTo(destination);
        JsonNode moved = itemOf(response.get("destination"), itemId);
        assertThat(moved.get("transferredFromTabId").asString()).isEqualTo(source);
        assertThat(moved.get("lineTotal").asString()).isEqualTo("12.00");
        assertThat(moved.get("splitGroup").asInt()).isEqualTo(1);
    }

    @Test
    void shouldWriteOneTrailRowPerMovementWithTheKindOfEachOperation() {
        String sodaId = createItem("Soda", "6.00", true);
        String first = openOnTable(createDiningTable("Salto um"));
        String second = openOnTable(createDiningTable("Salto dois"));
        String itemId = order(first, sodaId, 1);

        send(transfer(first, second, itemId), 200);
        String third = send(post(TABS + "/" + second + "/move", frontDeskToken,
                        "{\"diningTableId\":\"%s\"}".formatted(createDiningTable("Salto tres"))), 201)
                .get("id").asString();

        assertThat(trailOf(itemId)).containsExactly(
                first + " -> " + second + " TRANSFER",
                second + " -> " + third + " MOVE");
        JsonNode item = itemOf(send(get(TABS + "/" + third, waiterToken), 200), itemId);
        assertThat(item.get("transferredFromTabId").asString())
                .as("the column keeps the last hop; the trail keeps them all")
                .isEqualTo(second);
    }

    @Test
    void shouldRecordTheMergeKindAndLeaveTheAbsorbedTabPointingAtTheOtherOne() {
        String sodaId = createItem("Soda", "6.00", true);
        String merged = openOnTable(createDiningTable("Absorvida"));
        String receiving = openOnTable(createDiningTable("Que fica"));
        String itemId = order(merged, sodaId, 1);
        send(put(TABS + "/" + merged + "/guest-count", waiterToken, "{\"guestCount\":2}"), 200);
        send(put(TABS + "/" + receiving + "/guest-count", waiterToken, "{\"guestCount\":3}"), 200);

        JsonNode response = send(post(TABS + "/" + receiving + "/merge", waiterToken,
                "{\"mergedTabId\":\"%s\"}".formatted(merged)), 200);

        assertThat(response.get("id").asString()).isEqualTo(receiving);
        assertThat(response.get("guestCount").asInt()).isEqualTo(5);
        assertThat(trailOf(itemId)).containsExactly(merged + " -> " + receiving + " MERGE");
        JsonNode absorbed = send(get(TABS + "/" + merged, waiterToken), 200);
        assertThat(absorbed.get("status").asString()).isEqualTo("MERGED");
        assertThat(absorbed.get("mergedIntoTabId").asString()).isEqualTo(receiving);
        assertThat(absorbed.get("mergedAt").asString()).isNotBlank();
        assertThat(absorbed.get("mergedBy").asString()).isNotBlank();
    }

    @Test
    void shouldFreeTheTableOfTheAbsorbedTabSoAnotherTabOpensOnIt() {
        String sodaId = createItem("Soda", "6.00", true);
        String tableId = createDiningTable("Liberada");
        String merged = openOnTable(tableId);
        String receiving = openOnTable(createDiningTable("Recebe"));
        order(merged, sodaId, 1);

        send(post(TABS + "/" + receiving + "/merge", waiterToken,
                "{\"mergedTabId\":\"%s\"}".formatted(merged)), 200);

        assertThat(send(get(TABS + "?diningTableId=" + tableId, waiterToken), 200)).isEmpty();
        assertThat(send(post(TABS, waiterToken,
                        "{\"origin\":\"TABLE_SERVICE\",\"diningTableId\":\"%s\"}".formatted(tableId)), 201)
                .get("id").asString()).isNotBlank();
    }

    @Test
    void shouldAnswerABrandNewTabWhenTheTabChangesTable() {
        String sodaId = createItem("Soda", "6.00", true);
        String fromTableId = createDiningTable("Mesa velha");
        String toTableId = createDiningTable("Mesa nova");
        String tabId = openOnTable(fromTableId);
        String itemId = order(tabId, sodaId, 1);
        send(put(TABS + "/" + tabId + "/guest-count", waiterToken, "{\"guestCount\":5}"), 200);

        JsonNode moved = send(post(TABS + "/" + tabId + "/move", waiterToken,
                "{\"diningTableId\":\"%s\"}".formatted(toTableId)), 201);

        assertThat(moved.get("id").asString()).isNotEqualTo(tabId);
        assertThat(moved.get("diningTableId").asString()).isEqualTo(toTableId);
        assertThat(moved.get("subtotal").asString()).isEqualTo("6.00");
        assertThat(moved.get("guestCount").asInt())
                .as("the same party at another table keeps its number of guests (T17)")
                .isEqualTo(5);
        assertThat(itemOf(moved, itemId)).isNotNull();
        assertThat(tabIdOf(itemId)).isEqualTo(moved.get("id").asString());
        JsonNode old = send(get(TABS + "/" + tabId, waiterToken), 200);
        assertThat(old.get("status").asString()).isEqualTo("MERGED");
        assertThat(old.get("mergedIntoTabId").asString()).isEqualTo(moved.get("id").asString());
        assertThat(send(get(TABS + "?diningTableId=" + fromTableId, waiterToken), 200)).isEmpty();
    }

    /** The limitation task 3.5 left behind: the lock of the item filters by tab (section 7.4). */
    @Test
    void shouldLetTheKitchenAdvanceAnItemThatChangedTab() {
        String pizzaId = createItemAtStation("Pizza", "40.00", "PIZZA");
        String source = openOnTable(createDiningTable("Cozinha um"));
        String destination = openOnTable(createDiningTable("Cozinha dois"));
        String itemId = order(source, pizzaId, 1);
        send(post(KITCHEN_ITEMS + itemId + "/start", kitchenToken, ""), 200);

        send(transfer(source, destination, itemId), 200);

        JsonNode ready = send(post(KITCHEN_ITEMS + itemId + "/ready", kitchenToken, ""), 200);
        assertThat(ready.get("status").asString()).isEqualTo("READY");
        JsonNode queue = send(get("/api/kitchen/queue?station=PIZZA", kitchenToken), 200);
        JsonNode ticket = ticketOf(queue, itemId);
        assertThat(ticket.get("tabId").asString())
                .as("the ticket follows the item to the tab it arrived on")
                .isEqualTo(destination);
    }

    @Test
    void shouldLetTheReceptionMoveItemsAndRefuseTheKitchen() {
        String sodaId = createItem("Soda", "6.00", true);
        String source = openOnTable(createDiningTable("Recepcao"));
        String destination = openOnTable(createDiningTable("Recebe rec"));
        String firstItem = order(source, sodaId, 1);
        String secondItem = order(source, sodaId, 1);

        assertThat(exchange(post(TABS + "/" + source + "/transfer", frontDeskToken,
                "{\"toTabId\":\"%s\",\"itemIds\":[\"%s\"]}".formatted(destination, firstItem))).statusCode())
                .isEqualTo(200);
        assertThat(exchange(post(TABS + "/" + source + "/transfer", kitchenToken,
                "{\"toTabId\":\"%s\",\"itemIds\":[\"%s\"]}".formatted(destination, secondItem))).statusCode())
                .isEqualTo(403);
        assertThat(exchange(post(TABS + "/" + destination + "/merge", kitchenToken,
                "{\"mergedTabId\":\"%s\"}".formatted(source))).statusCode()).isEqualTo(403);
        assertThat(exchange(post(TABS + "/" + source + "/move", kitchenToken,
                "{\"diningTableId\":\"%s\"}".formatted(createDiningTable("Negada")))).statusCode()).isEqualTo(403);
    }

    @Test
    void shouldLeaveBothTabsUntouchedWhenOneItemOfTheListIsNotOnTheSourceTab() {
        String sodaId = createItem("Soda", "6.00", true);
        String source = openOnTable(createDiningTable("Tudo ou nada"));
        String destination = openOnTable(createDiningTable("Nada recebe"));
        String itemId = order(source, sodaId, 1);

        HttpResponse<String> response = exchange(post(TABS + "/" + source + "/transfer", waiterToken,
                "{\"toTabId\":\"%s\",\"itemIds\":[\"%s\",\"%s\"]}"
                        .formatted(destination, itemId, UUID.randomUUID())));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
        assertThat(codeOf(response)).isEqualTo("TAB_ITEM_NOT_FOUND");
        assertThat(tabIdOf(itemId)).isEqualTo(source);
        assertThat(trailOf(itemId)).isEmpty();
        assertThat(send(get(TABS + "/" + destination, waiterToken), 200).get("subtotal").asString())
                .isEqualTo("0.00");
    }

    @Test
    void shouldRefuseToTransferOutOfATabThatStartedClosing() {
        String sodaId = createItem("Soda", "6.00", true);
        String source = openOnTable(createDiningTable("Fechando"));
        String destination = openOnTable(createDiningTable("Recebe fech"));
        String itemId = order(source, sodaId, 1);
        send(post(TABS + "/" + source + "/closing", waiterToken, ""), 200);

        HttpResponse<String> response = exchange(post(TABS + "/" + source + "/transfer", waiterToken,
                "{\"toTabId\":\"%s\",\"itemIds\":[\"%s\"]}".formatted(destination, itemId)));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        assertThat(codeOf(response)).isEqualTo("TAB_NOT_OPEN");
        assertThat(tabIdOf(itemId)).isEqualTo(source);
    }

    @Test
    void shouldRefuseToChangeTableBeforeLookingAtTheDestinationWhenTheTabIsClosing() {
        String sodaId = createItem("Soda", "6.00", true);
        String tabId = openOnTable(createDiningTable("Fecha e move"));
        order(tabId, sodaId, 1);
        send(post(TABS + "/" + tabId + "/closing", waiterToken, ""), 200);

        HttpResponse<String> response = exchange(post(TABS + "/" + tabId + "/move", waiterToken,
                "{\"diningTableId\":\"%s\"}".formatted(deactivatedTable())));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        assertThat(codeOf(response))
                .as("the tab answers for its own status before it looks at the destination table")
                .isEqualTo("TAB_NOT_OPEN");
    }

    @Test
    void shouldRefuseToMergeATabWhoseFolioStillHasABalance() {
        String sodaId = createItem("Soda", "6.00", true);
        String merged = openOnTable(createDiningTable("Com saldo"));
        String receiving = openOnTable(createDiningTable("Recebe saldo"));
        order(merged, sodaId, 1);
        send(post(TABS + "/" + merged + "/closing", waiterToken, ""), 200);
        send(pay(merged, "PIX", "3.00", "merge-balance-" + suffix), 201);
        send(post(TABS + "/" + merged + "/reopen", waiterToken, "{\"reason\":\"cliente pediu mais\"}"), 200);

        HttpResponse<String> response = exchange(post(TABS + "/" + receiving + "/merge", waiterToken,
                "{\"mergedTabId\":\"%s\"}".formatted(merged)));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        assertThat(codeOf(response)).isEqualTo("FOLIO_BALANCE_NOT_ZERO");
        assertThat(send(get(TABS + "/" + merged, waiterToken), 200).get("status").asString()).isEqualTo("OPEN");
        assertThat(send(get(TABS + "/" + receiving, waiterToken), 200).get("subtotal").asString())
                .isEqualTo("0.00");
    }

    // ------------------------------------------------------------- helpers

    private java.net.http.HttpRequest.Builder transfer(String source, String destination, String itemId) {
        return post(TABS + "/" + source + "/transfer", waiterToken,
                "{\"toTabId\":\"%s\",\"itemIds\":[\"%s\"]}".formatted(destination, itemId));
    }

    private String deactivatedTable() {
        String tableId = createDiningTable("Inativa");
        send(post("/api/restaurant/dining-tables/" + tableId + "/deactivate", adminToken, ""), 200);
        return tableId;
    }

    private String createItemAtStation(String name, String price, String prepStation) {
        String categoryId = send(post("/api/restaurant/menu-categories", adminToken,
                        "{\"name\":\"Transfer %s %s\",\"displayOrder\":1}".formatted(suffix, UUID.randomUUID())), 201)
                .get("id").asString();
        return send(post("/api/restaurant/menu-items", adminToken, """
                {"categoryId":"%s","name":"%s %s","prepStation":"%s","soldByWeight":false,"price":"%s"}
                """.formatted(categoryId, name, suffix, prepStation, price)), 201)
                .get("id").asString();
    }

    private static JsonNode itemOf(JsonNode tab, String itemId) {
        for (JsonNode item : tab.get("items")) {
            if (itemId.equals(item.get("id").asString())) {
                return item;
            }
        }
        return null;
    }

    private static JsonNode ticketOf(JsonNode queue, String itemId) {
        for (JsonNode ticket : queue.get("items")) {
            if (itemId.equals(ticket.get("itemId").asString())) {
                return ticket;
            }
        }
        throw new AssertionError("No ticket for item " + itemId);
    }

    /** The column in the database, not what the aggregate holds in memory. */
    private String tabIdOf(String itemId) {
        return queryOne("select cast(tab_id as varchar) from tab_item where id = ?", itemId);
    }

    /** The trail of the item, oldest first, as "from -> to KIND". */
    private List<String> trailOf(String itemId) {
        List<String> trail = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select cast(from_tab_id as varchar), cast(to_tab_id as varchar), kind "
                                + "from tab_item_transfer where tab_item_id = ? order by transferred_at, id")) {
            statement.setObject(1, UUID.fromString(itemId));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    trail.add(rows.getString(1) + " -> " + rows.getString(2) + " " + rows.getString(3));
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
        return trail;
    }

    private String queryOne(String sql, String id) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.fromString(id));
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
