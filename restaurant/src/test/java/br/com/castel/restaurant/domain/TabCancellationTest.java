package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.CANCELLED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.FORTALEZA;
import static br.com.castel.restaurant.domain.TabFixtures.INVALID_CANCELLATION_REASON;
import static br.com.castel.restaurant.domain.TabFixtures.OTHER_WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_HAS_ACTIVE_ITEMS;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_ITEM_ALREADY_CANCELLED;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_ITEM_NOT_FOUND;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_NOT_OPEN;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.assertRejectedWith;
import static br.com.castel.restaurant.domain.TabFixtures.beer;
import static br.com.castel.restaurant.domain.TabFixtures.buffet;
import static br.com.castel.restaurant.domain.TabFixtures.cancelledTab;
import static br.com.castel.restaurant.domain.TabFixtures.cardTab;
import static br.com.castel.restaurant.domain.TabFixtures.grams;
import static br.com.castel.restaurant.domain.TabFixtures.oneUnit;
import static br.com.castel.restaurant.domain.TabFixtures.order;
import static br.com.castel.restaurant.domain.TabFixtures.steak;
import static br.com.castel.restaurant.domain.TabFixtures.tableTab;
import static br.com.castel.restaurant.domain.TabFixtures.wednesdayAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Cancelling an item and cancelling the whole tab, task 2.2 invariants 16 to 20, plus finding an
 * item and the order of the item list.
 */
class TabCancellationTest {

    private static final String REASON = "Cliente desistiu";

    @Nested
    @DisplayName("cancelling an item")
    class CancellingAnItem {

        @Test
        void shouldCancelAPendingItemRecordingWhoWhenAndWhy() {
            Tab tab = tableTab();
            TabItem item = order(tab, steak(), oneUnit());

            tab.cancelItem(item.id(), REASON, OTHER_WAITER, CANCELLED_AT);

            TabItem cancelled = tab.item(item.id());
            assertThat(cancelled.status()).isEqualTo(TabItemStatus.CANCELLED);
            assertThat(cancelled.isActive()).isFalse();
            assertThat(cancelled.cancelledBy()).contains(OTHER_WAITER);
            assertThat(cancelled.cancelledAt()).contains(CANCELLED_AT);
            assertThat(cancelled.cancellationReason()).contains(REASON);
        }

        @Test
        void shouldCancelADeliveredItem() {
            Tab tab = cardTab();
            TabItem plate = order(tab, buffet(), grams(437));

            tab.cancelItem(plate.id(), "Pesado na comanda errada", WAITER, CANCELLED_AT);

            assertThat(tab.item(plate.id()).status()).isEqualTo(TabItemStatus.CANCELLED);
        }

        @Test
        void shouldKeepTheCancelledItemOnTheTab() {
            Tab tab = tableTab();
            TabItem item = order(tab, steak(), oneUnit());

            tab.cancelItem(item.id(), REASON, WAITER, CANCELLED_AT);

            assertThat(tab.items()).extracting(TabItem::id).containsExactly(item.id());
        }

        @Test
        void shouldTrimTheReason() {
            Tab tab = tableTab();
            TabItem item = order(tab, steak(), oneUnit());

            tab.cancelItem(item.id(), "  " + REASON + "  ", WAITER, CANCELLED_AT);

            assertThat(tab.item(item.id()).cancellationReason()).contains(REASON);
        }

        @Test
        void shouldRejectAnItemAlreadyCancelledWithoutOverwritingWhoOrWhy() {
            Tab tab = tableTab();
            TabItem item = order(tab, steak(), oneUnit());
            tab.cancelItem(item.id(), REASON, WAITER, CANCELLED_AT);

            assertRejectedWith(
                    () -> tab.cancelItem(item.id(), "Outro motivo", OTHER_WAITER, wednesdayAt("13:00")),
                    TAB_ITEM_ALREADY_CANCELLED);

            TabItem cancelled = tab.item(item.id());
            assertThat(cancelled.cancelledBy()).contains(WAITER);
            assertThat(cancelled.cancelledAt()).contains(CANCELLED_AT);
            assertThat(cancelled.cancellationReason()).contains(REASON);
        }

        @Test
        void shouldRejectAnItemThatIsNotOnTheTab() {
            Tab tab = tableTab();
            order(tab, steak(), oneUnit());

            assertRejectedWith(
                    () -> tab.cancelItem(TabItemId.newId(), REASON, WAITER, CANCELLED_AT), TAB_ITEM_NOT_FOUND);
        }

        @Test
        void shouldRejectAnItemOfAnotherTab() {
            Tab tab = tableTab();
            Tab otherTab = cardTab();
            TabItem otherItem = order(otherTab, beer(), oneUnit());

            assertRejectedWith(() -> tab.cancelItem(otherItem.id(), REASON, WAITER, CANCELLED_AT), TAB_ITEM_NOT_FOUND);
        }

        /** Assumes the status of the tab is checked first, as it is in addItem (see the report). */
        @Test
        void shouldRejectCancellingAnItemWhenTheTabIsNotOpen() {
            Tab tab = tableTab();
            TabItem item = order(tab, steak(), oneUnit());
            tab.cancelItem(item.id(), REASON, WAITER, CANCELLED_AT);
            tab.cancel("Aberta por engano", WAITER, CANCELLED_AT);

            assertRejectedWith(() -> tab.cancelItem(item.id(), REASON, WAITER, CANCELLED_AT), TAB_NOT_OPEN);
        }

        @Test
        void shouldRejectAMissingReason() {
            Tab tab = tableTab();
            TabItem item = order(tab, steak(), oneUnit());

            assertRejectedWith(() -> tab.cancelItem(item.id(), null, WAITER, CANCELLED_AT), INVALID_CANCELLATION_REASON);
        }

        @Test
        void shouldRejectABlankReason() {
            Tab tab = tableTab();
            TabItem item = order(tab, steak(), oneUnit());

            assertRejectedWith(() -> tab.cancelItem(item.id(), "   ", WAITER, CANCELLED_AT), INVALID_CANCELLATION_REASON);
        }

        @Test
        void shouldRejectAReasonOf501Characters() {
            Tab tab = tableTab();
            TabItem item = order(tab, steak(), oneUnit());

            assertRejectedWith(
                    () -> tab.cancelItem(item.id(), "x".repeat(501), WAITER, CANCELLED_AT),
                    INVALID_CANCELLATION_REASON);
        }

        @Test
        void shouldAcceptAReasonOf500Characters() {
            Tab tab = tableTab();
            TabItem item = order(tab, steak(), oneUnit());

            tab.cancelItem(item.id(), "x".repeat(500), WAITER, CANCELLED_AT);

            assertThat(tab.item(item.id()).cancellationReason()).contains("x".repeat(500));
        }

        @Test
        void shouldLeaveTheItemActiveWhenTheReasonIsRejected() {
            Tab tab = tableTab();
            TabItem item = order(tab, steak(), oneUnit());

            assertRejectedWith(() -> tab.cancelItem(item.id(), "", WAITER, CANCELLED_AT), INVALID_CANCELLATION_REASON);

            assertThat(tab.item(item.id()).status()).isEqualTo(TabItemStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("cancelling the tab")
    class CancellingTheTab {

        @Test
        void shouldCancelAnEmptyTab() {
            Tab tab = tableTab();

            tab.cancel("Aberta na mesa errada", WAITER, CANCELLED_AT);

            assertThat(tab.status()).isEqualTo(TabStatus.CANCELLED);
        }

        @Test
        void shouldCancelATabWhoseItemsAreAllCancelled() {
            Tab tab = cardTab();
            TabItem beer = order(tab, beer(), oneUnit());
            tab.cancelItem(beer.id(), REASON, WAITER, CANCELLED_AT);

            tab.cancel("Cartão errado", WAITER, CANCELLED_AT);

            assertThat(tab.status()).isEqualTo(TabStatus.CANCELLED);
        }

        @Test
        void shouldRejectATabWithAPendingItem() {
            Tab tab = tableTab();
            order(tab, steak(), oneUnit());

            assertRejectedWith(() -> tab.cancel(REASON, WAITER, CANCELLED_AT), TAB_HAS_ACTIVE_ITEMS);
            assertThat(tab.status()).isEqualTo(TabStatus.OPEN);
        }

        @Test
        void shouldRejectATabWithADeliveredItem() {
            Tab tab = cardTab();
            order(tab, buffet(), grams(437));

            assertRejectedWith(() -> tab.cancel(REASON, WAITER, CANCELLED_AT), TAB_HAS_ACTIVE_ITEMS);
        }

        @Test
        void shouldRejectATabAlreadyCancelled() {
            Tab tab = cancelledTab();

            assertRejectedWith(() -> tab.cancel(REASON, WAITER, wednesdayAt("13:00")), TAB_NOT_OPEN);
        }

        @Test
        void shouldRejectABlankReason() {
            Tab tab = tableTab();

            assertRejectedWith(() -> tab.cancel("  ", WAITER, CANCELLED_AT), INVALID_CANCELLATION_REASON);
            assertThat(tab.status()).isEqualTo(TabStatus.OPEN);
        }

        @Test
        void shouldRejectAReasonOf501Characters() {
            Tab tab = tableTab();

            assertRejectedWith(() -> tab.cancel("x".repeat(501), WAITER, CANCELLED_AT), INVALID_CANCELLATION_REASON);
        }
    }

    @Nested
    @DisplayName("finding and listing items")
    class FindingAndListingItems {

        @Test
        void shouldRejectFindingAnItemThatIsNotOnTheTab() {
            Tab tab = tableTab();

            assertRejectedWith(() -> tab.item(TabItemId.newId()), TAB_ITEM_NOT_FOUND);
        }

        @Test
        void shouldListItemsByTheMomentTheyWereOrdered() {
            Tab tab = tableTab();
            Instant later = wednesdayAt("12:40");
            Instant earlier = wednesdayAt("12:10");
            TabItem second = tab.addItem(beer(), oneUnit(), WAITER, later, FORTALEZA);
            TabItem first = tab.addItem(steak(), oneUnit(), WAITER, earlier, FORTALEZA);

            assertThat(tab.items()).extracting(TabItem::id).containsExactly(first.id(), second.id());
        }

        @Test
        void shouldExposeTheItemsAsAnUnmodifiableList() {
            Tab tab = tableTab();
            TabItem item = order(tab, steak(), oneUnit());

            assertThatThrownBy(() -> tab.items().remove(item)).isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
