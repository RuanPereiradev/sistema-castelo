package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.CANCELLED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.CATEGORY_ID;
import static br.com.castel.restaurant.domain.TabFixtures.MERGED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.ORDERED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.PROPERTY_ID;
import static br.com.castel.restaurant.domain.TabFixtures.TRANSFERRED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.assertRejectedWith;
import static br.com.castel.restaurant.domain.TabFixtures.buffet;
import static br.com.castel.restaurant.domain.TabFixtures.cardTab;
import static br.com.castel.restaurant.domain.TabFixtures.grams;
import static br.com.castel.restaurant.domain.TabFixtures.oneUnit;
import static br.com.castel.restaurant.domain.TabFixtures.order;
import static br.com.castel.restaurant.domain.TabFixtures.pizza;
import static br.com.castel.restaurant.domain.TabFixtures.tableTab;
import static br.com.castel.restaurant.domain.TabFixtures.wednesdayAt;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.Money;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Task no-preparation-items: a menu item that needs no preparation is ordered already {@code READY}
 * (#1, #5), stays off the kitchen queue for good (#4, #8), refuses the undo (#11) and keeps its
 * frozen marking when it changes tab (#9). The status matrix of an item that does need preparation
 * lives in {@code TabItemKitchenTransitionTest}.
 */
class TabItemWithoutPreparationTest {

    private static final String INVALID_TAB_ITEM_TRANSITION = "INVALID_TAB_ITEM_TRANSITION";
    private static final Instant LATER = wednesdayAt("12:20");

    private final FakeTabBilling billing = new FakeTabBilling();

    // ------------------------------------------------------------------ ordered, every path through addItem

    @Test
    void shouldOrderAnItemWithoutPreparationAlreadyReadyAtTheMomentOfTheOrder() {
        TabItem item = order(tableTab(), soda(), oneUnit());

        assertThat(item.status()).isEqualTo(TabItemStatus.READY);
        assertThat(item.readyAt()).contains(ORDERED_AT);
        assertThat(item.preparationStartedAt()).isEmpty();
        assertThat(item.requiresPreparation()).isFalse();
        assertThat(item.isOnKitchenQueue()).isFalse();
    }

    @Test
    void shouldOrderAnItemWithoutPreparationAlreadyReadyOnASelfServiceCard() {
        TabItem item = order(cardTab(), soda(), oneUnit());

        assertThat(item.status()).isEqualTo(TabItemStatus.READY);
        assertThat(item.readyAt()).contains(ORDERED_AT);
        assertThat(item.preparationStartedAt()).isEmpty();
    }

    @Test
    void shouldKeepOrderingAnItemThatNeedsPreparationPendingOnTheKitchenQueue() {
        TabItem item = order(tableTab(), pizza(), oneUnit());

        assertThat(item.status()).isEqualTo(TabItemStatus.PENDING);
        assertThat(item.readyAt()).isEmpty();
        assertThat(item.requiresPreparation()).isTrue();
        assertThat(item.isOnKitchenQueue()).isTrue();
    }

    /** Decision #9: the weight rule of task 2.2 wins over the marking. */
    @Test
    void shouldKeepOrderingAWeighedItemDeliveredEvenWhenMarkedWithoutPreparation() {
        MenuItem buffet = buffet();
        buffet.changeRequiresPreparationTo(false);

        TabItem item = order(cardTab(), buffet, grams(450));

        assertThat(item.status()).isEqualTo(TabItemStatus.DELIVERED);
        assertThat(item.isOnKitchenQueue()).isFalse();
    }

    // ------------------------------------------------------------------ frozen at the order, #4

    @Test
    void shouldKeepAnOrderedItemOffTheQueueWhenTheMenuStartsRequiringPreparation() {
        MenuItem soda = soda();
        TabItem item = order(tableTab(), soda, oneUnit());

        soda.changeRequiresPreparationTo(true);

        assertThat(item.requiresPreparation()).isFalse();
        assertThat(item.status()).isEqualTo(TabItemStatus.READY);
        assertThat(item.isOnKitchenQueue()).isFalse();
    }

    @Test
    void shouldKeepAnOrderedItemOnTheQueueWhenTheMenuStopsRequiringPreparation() {
        MenuItem pizza = pizza();
        TabItem item = order(tableTab(), pizza, oneUnit());

        pizza.changeRequiresPreparationTo(false);

        assertThat(item.requiresPreparation()).isTrue();
        assertThat(item.status()).isEqualTo(TabItemStatus.PENDING);
        assertThat(item.isOnKitchenQueue()).isTrue();
    }

    // ------------------------------------------------------------------ what the item accepts

    /** Decision #11: it was born ready, there is no kitchen step to undo. */
    @Test
    void shouldRefuseUndoOnAnItemWithoutPreparationLeavingItReady() {
        Tab tab = tableTab();
        TabItemId itemId = order(tab, soda(), oneUnit()).id();

        assertRejectedWith(() -> tab.undoItemStatus(itemId, LATER), INVALID_TAB_ITEM_TRANSITION);

        assertThat(tab.item(itemId).status()).isEqualTo(TabItemStatus.READY);
        assertThat(tab.item(itemId).readyAt()).contains(ORDERED_AT);
    }

    @Test
    void shouldDeliverAnItemWithoutPreparation() {
        Tab tab = tableTab();
        TabItemId itemId = order(tab, soda(), oneUnit()).id();

        TabItemStatusChanged event = tab.deliverItem(itemId, LATER);

        assertThat(tab.item(itemId).status()).isEqualTo(TabItemStatus.DELIVERED);
        assertThat(tab.item(itemId).deliveredAt()).contains(LATER);
        assertThat(event.from()).isEqualTo(TabItemStatus.READY);
        assertThat(event.touchesReady()).isTrue();
        assertThat(event.reachesKitchenQueue()).isFalse();
    }

    @Test
    void shouldCancelAnItemWithoutPreparationWithoutReachingTheKitchenQueue() {
        Tab tab = tableTab();
        TabItemId itemId = order(tab, soda(), oneUnit()).id();

        tab.cancelItem(itemId, "Cliente desistiu", WAITER, CANCELLED_AT);

        assertThat(tab.item(itemId).status()).isEqualTo(TabItemStatus.CANCELLED);
        assertThat(TabItemCancelled.of(tab.id(), tab.item(itemId)).reachesKitchenQueue()).isFalse();
    }

    // ------------------------------------------------------------------ changing tab, #9

    @Test
    void shouldCarryTheMarkingWhenTheItemIsTransferred() {
        Tab source = tableTab();
        Tab destination = tableTab();
        TabItemId itemId = order(source, soda(), oneUnit()).id();

        TabTransferResult result = source.transferItemsTo(destination, Set.of(itemId), WAITER, TRANSFERRED_AT);

        TabItem moved = destination.item(itemId);
        assertThat(moved.requiresPreparation()).isFalse();
        assertThat(moved.status()).isEqualTo(TabItemStatus.READY);
        assertThat(moved.isOnKitchenQueue()).isFalse();
        assertThat(result.events()).singleElement()
                .satisfies(event -> assertThat(event.reachesKitchenQueue()).isFalse());
    }

    @Test
    void shouldCarryTheMarkingWhenTheTabIsMerged() {
        Tab receiving = tableTab();
        Tab absorbed = tableTab();
        TabItemId itemId = order(absorbed, soda(), oneUnit()).id();

        receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT);

        assertThat(receiving.item(itemId).requiresPreparation()).isFalse();
        assertThat(receiving.item(itemId).isOnKitchenQueue()).isFalse();
    }

    // ------------------------------------------------------------------ the event of the order, #8

    @Test
    void shouldDescribeTheOrderedItemAsReadyAndOffTheKitchenQueue() {
        Tab tab = tableTab();
        TabItem item = order(tab, soda(), oneUnit());

        TabItemOrdered event = TabItemOrdered.of(tab.id(), item);

        assertThat(event.status()).isEqualTo(TabItemStatus.READY);
        assertThat(event.requiresPreparation()).isFalse();
        assertThat(event.isReady()).isTrue();
        assertThat(event.reachesKitchenQueue()).isFalse();
    }

    // ------------------------------------------------------------------ fixtures

    /** The canned soda of the origin of the task: kept on the bar (#3), but nothing to prepare. */
    private static MenuItem soda() {
        MenuItem soda = MenuItem.soldByUnit(PROPERTY_ID, CATEGORY_ID, "Refrigerante lata", PrepStation.BAR,
                Money.of("6.00"));
        soda.changeRequiresPreparationTo(false);
        return soda;
    }
}
