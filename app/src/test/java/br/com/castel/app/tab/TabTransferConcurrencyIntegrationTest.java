package br.com.castel.app.tab;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.billing.api.FolioFacade;
import br.com.castel.billing.api.FolioId;
import br.com.castel.identity.api.Role;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Moving items between tabs under real concurrent requests (task 3.6), where the database decides.
 *
 * <p>A move holds two tabs at once, which is new: every other operation of a tab holds one. That is
 * where the deadlock would be, and the locks are taken in one fixed order of the two ids to keep it
 * from happening. These tests prove what that buys, and that the money stays right in each race:
 * the charge posted is the total of what was on the tab at that moment; no item is left stranded on
 * a {@code MERGED} tab; one line never ends up on two tabs; and two opposite merges do not hang.
 */
class TabTransferConcurrencyIntegrationTest extends AbstractTabClosingIntegrationTest {

    private static final int CONCURRENT_ORDERS = 5;
    private static final int ROUNDS = 5;

    @Autowired
    private FolioFacade folios;

    @Autowired
    private DataSource dataSource;

    private String kitchenToken;

    @BeforeEach
    void logInTheKitchen() {
        kitchenToken = accessTokenFor(createUser(Role.KITCHEN));
    }

    /** (a) The charge is the total of the items left behind, or the transfer loses and says so. */
    @Test
    void shouldPostTheTotalOfTheItemsLeftWhenATransferRacesTheStartOfClosing() throws Exception {
        String sodaId = createItem("Soda", "6.00", true);
        for (int round = 0; round < ROUNDS; round++) {
            String source = openOnTable(createDiningTable("Corrida a" + round));
            String destination = openOnTable(createDiningTable("Destino a" + round));
            order(source, sodaId, 1);
            String moving = order(source, sodaId, 1);

            List<HttpResponse<String>> responses = concurrently(List.of(
                    () -> exchange(transfer(source, destination, moving)),
                    () -> exchange(post(TABS + "/" + source + "/closing", waiterToken, ""))));

            HttpResponse<String> transferred = responses.get(0);
            HttpResponse<String> closing = responses.get(1);
            assertThat(closing.statusCode()).as(closing.body()).isEqualTo(200);
            if (transferred.statusCode() != 200) {
                assertThat(transferred.statusCode()).as(transferred.body()).isEqualTo(409);
                assertThat(codeOf(transferred)).isEqualTo("TAB_NOT_OPEN");
            }
            JsonNode tab = send(get(TABS + "/" + source, waiterToken), 200);
            assertThat(folios.findById(FolioId.of(tab.get("folioId").asString())).charges())
                    .singleElement()
                    .satisfies(charge -> assertThat(charge.amount().asString())
                            .as("the charge posted is the total of the tab as it was closed")
                            .isEqualTo(tab.get("total").asString()));
        }
    }

    /** (b) Decision #18 of task 2.2 carried to the merge: nothing is left stranded on a MERGED tab. */
    @Test
    void shouldLeaveNoActiveItemOnTheMergedTabWhenOrdersRaceTheMerge() throws Exception {
        String sodaId = createItem("Soda", "6.00", true);
        for (int round = 0; round < ROUNDS; round++) {
            String merged = openOnTable(createDiningTable("Absorv b" + round));
            String receiving = openOnTable(createDiningTable("Recebe b" + round));
            order(merged, sodaId, 1);
            String order = "{\"menuItemId\":\"%s\"}".formatted(sodaId);
            List<Callable<HttpResponse<String>>> requests = new ArrayList<>();
            for (int attempt = 0; attempt < CONCURRENT_ORDERS; attempt++) {
                requests.add(() -> exchange(post(TABS + "/" + merged + "/items", waiterToken, order)));
            }
            requests.add(() -> exchange(post(TABS + "/" + receiving + "/merge", waiterToken,
                    "{\"mergedTabId\":\"%s\"}".formatted(merged))));

            List<HttpResponse<String>> responses = concurrently(requests);

            assertThat(responses.get(CONCURRENT_ORDERS).statusCode())
                    .as(responses.get(CONCURRENT_ORDERS).body())
                    .isEqualTo(200);
            JsonNode absorbed = send(get(TABS + "/" + merged, waiterToken), 200);
            assertThat(absorbed.get("status").asString()).isEqualTo("MERGED");
            assertThat(absorbed.get("subtotal").asString())
                    .as("every item that got in before the merge came along")
                    .isEqualTo("0.00");
            long accepted = responses.subList(0, CONCURRENT_ORDERS).stream()
                    .filter(response -> response.statusCode() == 201).count();
            assertThat(send(get(TABS + "/" + receiving, waiterToken), 200).get("items"))
                    .hasSize((int) accepted + 1);
        }
    }

    /** (c) One line is on one tab: the loser is told the item is not where it looked for it. */
    @Test
    void shouldMoveTheLineToOneTabOnlyWhenTwoTransfersRaceForTheSameItem() throws Exception {
        String sodaId = createItem("Soda", "6.00", true);
        for (int round = 0; round < ROUNDS; round++) {
            String source = openOnTable(createDiningTable("Disputa c" + round));
            String first = openOnTable(createDiningTable("Primeiro c" + round));
            String second = openOnTable(createDiningTable("Segundo c" + round));
            String itemId = order(source, sodaId, 1);

            List<HttpResponse<String>> responses = concurrently(List.of(
                    () -> exchange(transfer(source, first, itemId)),
                    () -> exchange(transfer(source, second, itemId))));

            assertThat(responses).filteredOn(response -> response.statusCode() == 200).hasSize(1);
            for (HttpResponse<String> response : responses) {
                if (response.statusCode() != 200) {
                    assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
                    assertThat(codeOf(response)).isEqualTo("TAB_ITEM_NOT_FOUND");
                }
            }
            String holder = queryOne("select cast(tab_id as varchar) from tab_item where id = ?", itemId);
            assertThat(holder).isIn(first, second);
            assertThat(trailSizeOf(itemId)).as("one movement, one row on the trail").isEqualTo(1);
        }
    }

    /** (d) The reason the two tabs are locked in one fixed order of their ids. */
    @Test
    void shouldNotDeadlockWhenTwoOppositeMergesRunAtOnce() throws Exception {
        String sodaId = createItem("Soda", "6.00", true);
        for (int round = 0; round < ROUNDS; round++) {
            String one = openOnTable(createDiningTable("Lado d" + round));
            String other = openOnTable(createDiningTable("Outro d" + round));
            order(one, sodaId, 1);
            order(other, sodaId, 1);

            List<HttpResponse<String>> responses = concurrently(List.of(
                    () -> exchange(post(TABS + "/" + one + "/merge", waiterToken,
                            "{\"mergedTabId\":\"%s\"}".formatted(other))),
                    () -> exchange(post(TABS + "/" + other + "/merge", waiterToken,
                            "{\"mergedTabId\":\"%s\"}".formatted(one)))));

            assertThat(responses).filteredOn(response -> response.statusCode() == 200).hasSize(1);
            for (HttpResponse<String> response : responses) {
                if (response.statusCode() != 200) {
                    assertThat(response.statusCode())
                            .as("a deadlock would surface as 500, never as the conflict of the domain: "
                                    + response.body())
                            .isEqualTo(409);
                    assertThat(codeOf(response)).isEqualTo("TAB_NOT_OPEN");
                }
            }
            JsonNode first = send(get(TABS + "/" + one, waiterToken), 200);
            JsonNode last = send(get(TABS + "/" + other, waiterToken), 200);
            assertThat(List.of(first.get("status").asString(), last.get("status").asString()))
                    .as("one absorbed the other, and only one of them")
                    .containsExactlyInAnyOrder("OPEN", "MERGED");
            assertThat(new BigDecimal(first.get("subtotal").asString())
                            .add(new BigDecimal(last.get("subtotal").asString())))
                    .as("no money was lost or duplicated")
                    .isEqualByComparingTo("12.00");
        }
    }

    /** (e) Section 7.4: the kitchen looks for the item again where it is now, instead of failing. */
    @Test
    void shouldLetTheKitchenAdvanceTheItemWhenATransferRacesTheTransition() throws Exception {
        String pizzaId = createItemAtStation("Pizza", "40.00", "PIZZA");
        for (int round = 0; round < ROUNDS; round++) {
            String source = openOnTable(createDiningTable("Fogo e" + round));
            String destination = openOnTable(createDiningTable("Chega e" + round));
            String itemId = order(source, pizzaId, 1);

            List<HttpResponse<String>> responses = concurrently(List.of(
                    () -> exchange(post("/api/kitchen/items/" + itemId + "/ready", kitchenToken, "")),
                    () -> exchange(transfer(source, destination, itemId))));

            for (HttpResponse<String> response : responses) {
                assertThat(response.statusCode())
                        .as("neither request may answer 500: " + response.body())
                        .isLessThan(500);
            }
            assertThat(responses.get(1).statusCode()).as(responses.get(1).body()).isEqualTo(200);
            HttpResponse<String> kitchen = responses.get(0);
            if (kitchen.statusCode() != 200) {
                assertThat(codeOf(kitchen)).isEqualTo("TAB_ITEM_NOT_FOUND");
            }
            /* Whoever lost the race, the kitchen reaches the item on the tab it is on now. */
            JsonNode ready = send(post("/api/kitchen/items/" + itemId + "/ready", kitchenToken, ""),
                    kitchen.statusCode() == 200 ? 409 : 200);
            assertThat(ready).isNotNull();
        }
    }

    // ------------------------------------------------------------- helpers

    private java.net.http.HttpRequest.Builder transfer(String source, String destination, String itemId) {
        return post(TABS + "/" + source + "/transfer", waiterToken,
                "{\"toTabId\":\"%s\",\"itemIds\":[\"%s\"]}".formatted(destination, itemId));
    }

    private String createItemAtStation(String name, String price, String prepStation) {
        String categoryId = send(post("/api/restaurant/menu-categories", adminToken,
                        "{\"name\":\"Race %s %s\",\"displayOrder\":1}".formatted(suffix, java.util.UUID.randomUUID())),
                        201)
                .get("id").asString();
        return send(post("/api/restaurant/menu-items", adminToken, """
                {"categoryId":"%s","name":"%s %s","prepStation":"%s","soldByWeight":false,"price":"%s"}
                """.formatted(categoryId, name, suffix, prepStation, price)), 201)
                .get("id").asString();
    }

    private int trailSizeOf(String itemId) {
        return Integer.parseInt(queryOne(
                "select cast(count(*) as varchar) from tab_item_transfer where tab_item_id = ?", itemId));
    }

    private String queryOne(String sql, String id) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, java.util.UUID.fromString(id));
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
