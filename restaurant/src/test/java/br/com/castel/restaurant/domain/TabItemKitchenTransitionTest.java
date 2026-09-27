package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.CANCELLED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.CLOSED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.CLOSING_AT;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_ITEM_ALREADY_CANCELLED;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_ITEM_NOT_FOUND;
import static br.com.castel.restaurant.domain.TabFixtures.TEN_PERCENT;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.ConflictException;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Kitchen display transitions of a tab item through the {@link Tab} aggregate, task 3.5 section 3:
 * the status x operation matrix, the instants recorded and erased, the order of the checks, undo
 * going back to the status before the last touch (decision #21), the weighed item off the queue and
 * the tab status not blocking the kitchen (invariant 4, F12).
 */
class TabItemKitchenTransitionTest {

    static final String INVALID_TAB_ITEM_TRANSITION = "INVALID_TAB_ITEM_TRANSITION";

    static final Instant STARTED_AT = wednesdayAt("12:10");
    static final Instant READY_AT = wednesdayAt("12:20");
    static final Instant UNDONE_AT = wednesdayAt("12:22");
    static final Instant DELIVERED_AT = wednesdayAt("12:25");
    static final Instant LATER = wednesdayAt("12:40");

    /** The four kitchen display operations of {@link Tab}, so the matrix can be walked. */
    enum Operation {
        START {
            @Override
            TabItemStatusChanged on(Tab tab, TabItemId itemId, Instant at) {
                return tab.startItemPreparation(itemId, at);
            }
        },
        READY {
            @Override
            TabItemStatusChanged on(Tab tab, TabItemId itemId, Instant at) {
                return tab.markItemReady(itemId, at);
            }
        },
        DELIVER {
            @Override
            TabItemStatusChanged on(Tab tab, TabItemId itemId, Instant at) {
                return tab.deliverItem(itemId, at);
            }
        },
        UNDO {
            @Override
            TabItemStatusChanged on(Tab tab, TabItemId itemId, Instant at) {
                return tab.undoItemStatus(itemId, at);
            }
        };

        abstract TabItemStatusChanged on(Tab tab, TabItemId itemId, Instant at);
    }

    /** How a pizza reached its current status. READY has two paths, since undo depends on it (#21). */
    enum Path {
        PENDING(TabItemStatus.PENDING),
        IN_PREPARATION(TabItemStatus.IN_PREPARATION),
        READY_AFTER_PREPARATION(TabItemStatus.READY),
        READY_SKIPPING_PREPARATION(TabItemStatus.READY),
        DELIVERED(TabItemStatus.DELIVERED),
        CANCELLED(TabItemStatus.CANCELLED);

        final TabItemStatus status;

        Path(TabItemStatus status) {
            this.status = status;
        }

        TabItemId walk(Tab tab) {
            TabItemId itemId = order(tab, pizza(), oneUnit()).id();
            switch (this) {
                case PENDING -> { }
                case IN_PREPARATION -> tab.startItemPreparation(itemId, STARTED_AT);
                case READY_AFTER_PREPARATION -> {
                    tab.startItemPreparation(itemId, STARTED_AT);
                    tab.markItemReady(itemId, READY_AT);
                }
                case READY_SKIPPING_PREPARATION -> tab.markItemReady(itemId, READY_AT);
                case DELIVERED -> tab.deliverItem(itemId, DELIVERED_AT);
                case CANCELLED -> tab.cancelItem(itemId, "Cliente desistiu", WAITER, CANCELLED_AT);
            }
            return itemId;
        }
    }

    // ------------------------------------------------------------------ accepted, with instants

    @Nested
    @DisplayName("accepted transitions")
    class Accepted {

        @Test
        void shouldStartPreparationOfAPendingItemRecordingTheInstant() {
            Tab tab = tableTab();
            TabItemId itemId = Path.PENDING.walk(tab);

            tab.startItemPreparation(itemId, STARTED_AT);

            TabItem item = tab.item(itemId);
            assertThat(item.status()).isEqualTo(TabItemStatus.IN_PREPARATION);
            assertThat(item.preparationStartedAt()).contains(STARTED_AT);
            assertThat(item.readyAt()).isEmpty();
            assertThat(item.isOnKitchenQueue()).isTrue();
        }

        @Test
        void shouldMarkAnItemInPreparationReadyKeepingTheStartInstant() {
            Tab tab = tableTab();
            TabItemId itemId = Path.IN_PREPARATION.walk(tab);

            tab.markItemReady(itemId, READY_AT);

            TabItem item = tab.item(itemId);
            assertThat(item.status()).isEqualTo(TabItemStatus.READY);
            assertThat(item.readyAt()).contains(READY_AT);
            assertThat(item.preparationStartedAt()).contains(STARTED_AT);
            assertThat(item.isOnKitchenQueue()).isTrue();
        }

        @Test
        void shouldMarkAPendingItemReadyDirectlyLeavingTheStartInstantEmpty() {
            Tab tab = tableTab();
            TabItemId itemId = Path.PENDING.walk(tab);

            tab.markItemReady(itemId, READY_AT);

            TabItem item = tab.item(itemId);
            assertThat(item.status()).isEqualTo(TabItemStatus.READY);
            assertThat(item.readyAt()).contains(READY_AT);
            assertThat(item.preparationStartedAt()).isEmpty();
        }

        @ParameterizedTest(name = "from {0}")
        @EnumSource(value = Path.class,
                names = {"PENDING", "IN_PREPARATION", "READY_AFTER_PREPARATION", "READY_SKIPPING_PREPARATION"})
        void shouldDeliverAnItemStillOnTheQueueRecordingTheInstantAndTakingItOff(Path path) {
            Tab tab = tableTab();
            TabItemId itemId = path.walk(tab);

            tab.deliverItem(itemId, LATER);

            TabItem item = tab.item(itemId);
            assertThat(item.status()).isEqualTo(TabItemStatus.DELIVERED);
            assertThat(item.deliveredAt()).contains(LATER);
            assertThat(item.isOnKitchenQueue()).isFalse();
        }

        @Test
        void shouldUndoPreparationBackToPendingErasingTheStartInstant() {
            Tab tab = tableTab();
            TabItemId itemId = Path.IN_PREPARATION.walk(tab);

            tab.undoItemStatus(itemId, UNDONE_AT);

            TabItem item = tab.item(itemId);
            assertThat(item.status()).isEqualTo(TabItemStatus.PENDING);
            assertThat(item.preparationStartedAt()).isEmpty();
            assertThat(item.readyAt()).isEmpty();
        }

        @Test
        void shouldUndoReadyBackToPreparationErasingOnlyTheReadyInstant() {
            Tab tab = tableTab();
            TabItemId itemId = Path.READY_AFTER_PREPARATION.walk(tab);

            tab.undoItemStatus(itemId, UNDONE_AT);

            TabItem item = tab.item(itemId);
            assertThat(item.status()).isEqualTo(TabItemStatus.IN_PREPARATION);
            assertThat(item.readyAt()).isEmpty();
            assertThat(item.preparationStartedAt()).contains(STARTED_AT);
        }

        @Test
        void shouldUndoReadyThatSkippedPreparationBackToPending() {
            Tab tab = tableTab();
            TabItemId itemId = Path.READY_SKIPPING_PREPARATION.walk(tab);

            tab.undoItemStatus(itemId, UNDONE_AT);

            TabItem item = tab.item(itemId);
            assertThat(item.status()).isEqualTo(TabItemStatus.PENDING);
            assertThat(item.readyAt()).isEmpty();
            assertThat(item.preparationStartedAt()).isEmpty();
        }

        @Test
        void shouldRecordTheNewReadyInstantWhenMarkedReadyAgainAfterUndo() {
            Tab tab = tableTab();
            TabItemId itemId = Path.READY_AFTER_PREPARATION.walk(tab);
            tab.undoItemStatus(itemId, UNDONE_AT);

            tab.markItemReady(itemId, LATER);

            assertThat(tab.item(itemId).readyAt()).contains(LATER);
        }

        @Test
        void shouldRecordTheNewStartInstantWhenStartedAgainAfterUndo() {
            Tab tab = tableTab();
            TabItemId itemId = Path.IN_PREPARATION.walk(tab);
            tab.undoItemStatus(itemId, UNDONE_AT);

            tab.startItemPreparation(itemId, LATER);

            assertThat(tab.item(itemId).preparationStartedAt()).contains(LATER);
        }
    }

    // ------------------------------------------------------------------ the returned event

    @Nested
    @DisplayName("returned event")
    class ReturnedEvent {

        static Stream<Arguments> acceptedTransitions() {
            return Stream.of(
                    arguments(Path.PENDING, Operation.START, TabItemStatus.PENDING, TabItemStatus.IN_PREPARATION),
                    arguments(Path.PENDING, Operation.READY, TabItemStatus.PENDING, TabItemStatus.READY),
                    arguments(Path.PENDING, Operation.DELIVER, TabItemStatus.PENDING, TabItemStatus.DELIVERED),
                    arguments(Path.IN_PREPARATION, Operation.READY, TabItemStatus.IN_PREPARATION, TabItemStatus.READY),
                    arguments(Path.IN_PREPARATION, Operation.DELIVER, TabItemStatus.IN_PREPARATION,
                            TabItemStatus.DELIVERED),
                    arguments(Path.IN_PREPARATION, Operation.UNDO, TabItemStatus.IN_PREPARATION, TabItemStatus.PENDING),
                    arguments(Path.READY_AFTER_PREPARATION, Operation.DELIVER, TabItemStatus.READY,
                            TabItemStatus.DELIVERED),
                    arguments(Path.READY_AFTER_PREPARATION, Operation.UNDO, TabItemStatus.READY,
                            TabItemStatus.IN_PREPARATION),
                    arguments(Path.READY_SKIPPING_PREPARATION, Operation.UNDO, TabItemStatus.READY,
                            TabItemStatus.PENDING));
        }

        @ParameterizedTest(name = "{0} {1}: {2} -> {3}")
        @MethodSource("acceptedTransitions")
        void shouldReturnTheEventWithTheStatusBeforeAndAfter(Path path, Operation operation, TabItemStatus from,
                TabItemStatus to) {
            Tab tab = tableTab();
            TabItemId itemId = path.walk(tab);

            TabItemStatusChanged event = operation.on(tab, itemId, LATER);

            assertThat(event.from()).isEqualTo(from);
            assertThat(event.to()).isEqualTo(to);
            assertThat(event.to()).isEqualTo(tab.item(itemId).status());
        }

        @Test
        void shouldIdentifyTheTabTheItemAndTheStationAndCarryTheInstantOfTheTransition() {
            Tab tab = tableTab();
            TabItemId itemId = Path.PENDING.walk(tab);

            TabItemStatusChanged event = tab.startItemPreparation(itemId, STARTED_AT);

            assertThat(event.tabId()).isEqualTo(tab.id());
            assertThat(event.itemId()).isEqualTo(itemId);
            assertThat(event.station()).isEqualTo(PrepStation.PIZZA);
            assertThat(event.occurredAt()).isEqualTo(STARTED_AT);
        }
    }

    // ------------------------------------------------------------------ refused, the matrix

    @Nested
    @DisplayName("refused transitions")
    class Refused {

        static Stream<Arguments> invalidTransitions() {
            return Stream.of(
                    arguments(Path.PENDING, Operation.UNDO),
                    arguments(Path.IN_PREPARATION, Operation.START),
                    arguments(Path.READY_AFTER_PREPARATION, Operation.START),
                    arguments(Path.READY_AFTER_PREPARATION, Operation.READY),
                    arguments(Path.READY_SKIPPING_PREPARATION, Operation.START),
                    arguments(Path.READY_SKIPPING_PREPARATION, Operation.READY),
                    arguments(Path.DELIVERED, Operation.START),
                    arguments(Path.DELIVERED, Operation.READY),
                    arguments(Path.DELIVERED, Operation.DELIVER),
                    arguments(Path.DELIVERED, Operation.UNDO));
        }

        @ParameterizedTest(name = "{0} {1}")
        @MethodSource("invalidTransitions")
        void shouldRefuseATransitionTheStatusDoesNotAccept(Path path, Operation operation) {
            Tab tab = tableTab();
            TabItemId itemId = path.walk(tab);

            assertRejectedWith(() -> operation.on(tab, itemId, LATER), INVALID_TAB_ITEM_TRANSITION);
        }

        @ParameterizedTest(name = "{0} {1}")
        @MethodSource("invalidTransitions")
        void shouldLeaveTheItemUntouchedWhenATransitionIsRefused(Path path, Operation operation) {
            Tab tab = tableTab();
            TabItemId itemId = path.walk(tab);
            TabItem before = tab.item(itemId);
            TabItemStatus status = before.status();
            var startedAt = before.preparationStartedAt();
            var readyAt = before.readyAt();
            var deliveredAt = before.deliveredAt();

            assertThatThrownBy(() -> operation.on(tab, itemId, LATER));

            TabItem after = tab.item(itemId);
            assertThat(after.status()).isEqualTo(status);
            assertThat(after.preparationStartedAt()).isEqualTo(startedAt);
            assertThat(after.readyAt()).isEqualTo(readyAt);
            assertThat(after.deliveredAt()).isEqualTo(deliveredAt);
        }

        @Test
        void shouldRefuseAnInvalidTransitionAsAConflict() {
            Tab tab = tableTab();
            TabItemId itemId = Path.DELIVERED.walk(tab);

            assertThatThrownBy(() -> tab.undoItemStatus(itemId, LATER))
                    .isInstanceOf(InvalidTabItemTransitionException.class)
                    .isInstanceOf(ConflictException.class);
        }

        static Stream<Arguments> cancelledFromEveryStatus() {
            return Stream.of(Path.PENDING, Path.IN_PREPARATION, Path.READY_AFTER_PREPARATION, Path.DELIVERED)
                    .flatMap(before -> Arrays.stream(Operation.values()).map(operation -> arguments(before, operation)));
        }

        @ParameterizedTest(name = "cancelled after {0}, {1}")
        @MethodSource("cancelledFromEveryStatus")
        void shouldRefuseEveryTransitionOfACancelledItemAsAlreadyCancelled(Path before, Operation operation) {
            Tab tab = tableTab();
            TabItemId itemId = before.walk(tab);
            tab.cancelItem(itemId, "Cliente desistiu", WAITER, CANCELLED_AT);

            assertRejectedWith(() -> operation.on(tab, itemId, LATER), TAB_ITEM_ALREADY_CANCELLED);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Operation.class)
        void shouldRefuseATransitionOfAnItemNotOnTheTab(Operation operation) {
            Tab tab = tableTab();
            Path.PENDING.walk(tab);

            assertRejectedWith(() -> operation.on(tab, TabItemId.newId(), LATER), TAB_ITEM_NOT_FOUND);
        }

        @Test
        void shouldRefuseATransitionOfAnItemThatBelongsToAnotherTab() {
            Tab tab = tableTab();
            TabItemId itemOfAnotherTab = Path.PENDING.walk(cardTab());

            assertRejectedWith(() -> tab.markItemReady(itemOfAnotherTab, LATER), TAB_ITEM_NOT_FOUND);
        }
    }

    // ------------------------------------------------------------------ weighed item, invariant 5

    @Nested
    @DisplayName("item sold by weight")
    class SoldByWeight {

        @Test
        void shouldKeepAWeighedItemOffTheKitchenQueue() {
            Tab tab = cardTab();

            TabItem item = order(tab, buffet(), grams(450));

            assertThat(item.status()).isEqualTo(TabItemStatus.DELIVERED);
            assertThat(item.isOnKitchenQueue()).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Operation.class)
        void shouldRefuseEveryKitchenTransitionOfAWeighedItem(Operation operation) {
            Tab tab = cardTab();
            TabItemId itemId = order(tab, buffet(), grams(450)).id();

            assertRejectedWith(() -> operation.on(tab, itemId, LATER), INVALID_TAB_ITEM_TRANSITION);
        }
    }

    // ------------------------------------------------------------------ tab status, invariant 4

    @Nested
    @DisplayName("independent of the tab status")
    class TabStatusDoesNotBlock {

        static Stream<Arguments> transitionsOnATabThatIsNotOpen() {
            return Stream.of(TabStatus.CLOSING, TabStatus.CLOSED).flatMap(status -> Stream.of(
                    arguments(status, Path.PENDING, Operation.START, TabItemStatus.IN_PREPARATION),
                    arguments(status, Path.PENDING, Operation.READY, TabItemStatus.READY),
                    arguments(status, Path.READY_AFTER_PREPARATION, Operation.UNDO, TabItemStatus.IN_PREPARATION),
                    arguments(status, Path.READY_AFTER_PREPARATION, Operation.DELIVER, TabItemStatus.DELIVERED)));
        }

        /** The tab is really closed, by the closing of task 3.2: the kitchen transition never reads it. */
        @ParameterizedTest(name = "tab {0}, {1} {2}")
        @MethodSource("transitionsOnATabThatIsNotOpen")
        void shouldMoveTheItemWhateverTheTabStatus(TabStatus tabStatus, Path path, Operation operation,
                TabItemStatus expected) {
            Tab tab = tableTab();
            TabItemId itemId = path.walk(tab);
            bringTo(tab, tabStatus);
            assertThat(tab.status()).isEqualTo(tabStatus);

            operation.on(tab, itemId, LATER);

            assertThat(tab.item(itemId).status()).isEqualTo(expected);
        }

        /** CLOSING by the pre-bill; CLOSED once the whole total is paid. */
        private static void bringTo(Tab tab, TabStatus tabStatus) {
            FakeTabBilling billing = new FakeTabBilling();
            tab.startClosing(TEN_PERCENT, billing, WAITER, CLOSING_AT);
            if (tabStatus == TabStatus.CLOSED) {
                tab.receivePayment(PaymentMethod.PIX, tab.total(TEN_PERCENT), "kitchen-closed", billing);
                tab.close(billing, WAITER, CLOSED_AT);
            }
        }

        @Test
        void shouldReportAlreadyCancelledForAnItemOfACancelledTab() {
            Tab tab = tableTab();
            TabItemId itemId = Path.CANCELLED.walk(tab);
            tab.cancel("Aberta na mesa errada", WAITER, CANCELLED_AT);

            assertRejectedWith(() -> tab.startItemPreparation(itemId, LATER), TAB_ITEM_ALREADY_CANCELLED);
        }
    }
}
