package br.com.castel.app.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Task no-preparation-items through the routes: the marking set by the administration, frozen on the
 * tab item, read by the queue of every station. The status rules live in
 * {@code TabItemWithoutPreparationTest}; this proves the column, the queue filter and the codes.
 */
class NoPreparationItemsHttpIntegrationTest extends AbstractKitchenDisplayIntegrationTest {

    private static final String MENU_ITEMS = "/api/restaurant/menu-items";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * #2 and #4: V15 adds the column with {@code DEFAULT true NOT NULL}, so every menu item and every
     * tab item that existed before the migration keeps going to the kitchen.
     */
    @Test
    void shouldCreateAnItemThatNeedsPreparationWhenTheFieldIsAbsent() {
        JsonNode created = send(post(MENU_ITEMS, adminToken, """
                {"categoryId":"%s","name":"Sem campo %s","prepStation":"BAR","soldByWeight":false,"price":"6.00"}
                """.formatted(createCategory(), suffix)), 201);

        assertThat(created.get("requiresPreparation").asBoolean()).isTrue();
    }

    @Test
    void shouldStoreTheMarkingChangedThroughThePatch() {
        String itemId = createItem(createCategory(), "Refri", "BAR", false);

        JsonNode changed = send(patch(MENU_ITEMS + "/" + itemId + "/requires-preparation", adminToken,
                "{\"requiresPreparation\":false}"), 200);

        assertThat(changed.get("requiresPreparation").asBoolean()).isFalse();
        assertThat(send(get(MENU_ITEMS + "/" + itemId, adminToken), 200).get("requiresPreparation").asBoolean())
                .isFalse();
    }

    @Test
    void shouldRefuseThePatchWithoutTheMarking() {
        String itemId = createItem(createCategory(), "Refri", "BAR", false);

        assertThat(codeOf(patch(MENU_ITEMS + "/" + itemId + "/requires-preparation", adminToken, "{}"), 400))
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void shouldOrderAnItemWithoutPreparationReadyAndKeepItOffTheQueueOfEveryStation() {
        String sodaId = createItemWithoutPreparation(createCategory(), "Refri", "BAR");
        String tabId = openTabOnNewTable("Pronto");

        String soda = order(tabId, sodaId);

        JsonNode item = itemOf(send(get(TABS + "/" + tabId, waiterToken), 200), soda);
        assertThat(item.get("status").asString()).isEqualTo("READY");
        assertThat(item.get("readyAt").asString()).isEqualTo(item.get("orderedAt").asString());
        assertThat(item.get("preparationStartedAt").isNull()).isTrue();
        for (String station : new String[] {"KITCHEN", "PIZZA", "BAR"}) {
            assertThat(containsItem(send(get(KITCHEN + "/queue?station=" + station, kitchenToken), 200), soda))
                    .as(station)
                    .isFalse();
        }
    }

    @Test
    void shouldRefuseTheUndoOfAnItemWithoutPreparation() {
        String tabId = openTabOnNewTable("Desfaz");
        String soda = order(tabId, createItemWithoutPreparation(createCategory(), "Refri", "BAR"));

        assertThat(codeOf(post(KITCHEN + "/items/" + soda + "/undo", kitchenToken, ""), 409))
                .isEqualTo("INVALID_TAB_ITEM_TRANSITION");
    }

    /** #4: the queue reads the marking frozen on the tab item, never the one on the menu today. */
    @Test
    void shouldKeepAnOrderedItemOnTheQueueWhenTheMenuStopsRequiringPreparation() {
        String pizzaId = createItem(createCategory(), "Pizza", "PIZZA", false);
        String tabId = openTabOnNewTable("Congela");
        String pizza = order(tabId, pizzaId);

        send(patch(MENU_ITEMS + "/" + pizzaId + "/requires-preparation", adminToken,
                "{\"requiresPreparation\":false}"), 200);

        assertThat(containsItem(send(get(KITCHEN + "/queue?station=PIZZA", kitchenToken), 200), pizza)).isTrue();
        assertThat(itemOf(send(get(TABS + "/" + tabId, waiterToken), 200), pizza).get("status").asString())
                .isEqualTo("PENDING");
    }

    private static JsonNode itemOf(JsonNode tab, String itemId) {
        return StreamSupport.stream(tab.get("items").spliterator(), false)
                .filter(item -> item.get("id").asString().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No item " + itemId + " in " + tab));
    }
}
