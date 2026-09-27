package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.CANCELLED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.ORDERED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.buffet;
import static br.com.castel.restaurant.domain.TabFixtures.cardTab;
import static br.com.castel.restaurant.domain.TabFixtures.grams;
import static br.com.castel.restaurant.domain.TabFixtures.oneUnit;
import static br.com.castel.restaurant.domain.TabFixtures.order;
import static br.com.castel.restaurant.domain.TabFixtures.pizza;
import static br.com.castel.restaurant.domain.TabFixtures.tableTab;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.restaurant.api.PrepStation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The item events of task 3.5 section 4: what {@code of} copies from the item, whether an ordered
 * item reaches the kitchen queue and which status change the waiter topic cares about.
 */
class TabItemKitchenEventsTest {

    @Test
    void shouldDescribeAnOrderedItemAtTheInstantItWasOrdered() {
        Tab tab = tableTab();
        TabItem item = order(tab, pizza(), oneUnit());

        TabItemOrdered event = TabItemOrdered.of(tab.id(), item);

        assertThat(event.tabId()).isEqualTo(tab.id());
        assertThat(event.itemId()).isEqualTo(item.id());
        assertThat(event.station()).isEqualTo(PrepStation.PIZZA);
        assertThat(event.status()).isEqualTo(TabItemStatus.PENDING);
        assertThat(event.occurredAt()).isEqualTo(ORDERED_AT);
        assertThat(event.reachesKitchenQueue()).isTrue();
    }

    @Test
    void shouldNotReachTheKitchenQueueWhenTheOrderedItemIsSoldByWeight() {
        Tab tab = cardTab();
        TabItem item = order(tab, buffet(), grams(450));

        TabItemOrdered event = TabItemOrdered.of(tab.id(), item);

        assertThat(event.reachesKitchenQueue()).isFalse();
    }

    @Test
    void shouldDescribeACancelledItemWithTheReasonAtTheInstantItWasCancelled() {
        Tab tab = tableTab();
        TabItem item = order(tab, pizza(), oneUnit());
        tab.cancelItem(item.id(), "Cliente desistiu", WAITER, CANCELLED_AT);

        TabItemCancelled event = TabItemCancelled.of(tab.id(), tab.item(item.id()));

        assertThat(event.tabId()).isEqualTo(tab.id());
        assertThat(event.itemId()).isEqualTo(item.id());
        assertThat(event.station()).isEqualTo(PrepStation.PIZZA);
        assertThat(event.reason()).isEqualTo("Cliente desistiu");
        assertThat(event.occurredAt()).isEqualTo(CANCELLED_AT);
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "PENDING,        IN_PREPARATION, false",
            "IN_PREPARATION, PENDING,        false",
            "IN_PREPARATION, DELIVERED,      false",
            "IN_PREPARATION, READY,          true",
            "PENDING,        READY,          true",
            "READY,          IN_PREPARATION, true",
            "READY,          DELIVERED,      true"
    })
    void shouldTouchReadyWhenTheItemEntersOrLeavesReady(TabItemStatus from, TabItemStatus to, boolean expected) {
        Tab tab = tableTab();
        TabItem item = order(tab, pizza(), oneUnit());
        TabItemStatusChanged event = new TabItemStatusChanged(tab.id(), item.id(), PrepStation.PIZZA, from, to,
                ORDERED_AT);

        assertThat(event.touchesReady()).isEqualTo(expected);
    }
}
