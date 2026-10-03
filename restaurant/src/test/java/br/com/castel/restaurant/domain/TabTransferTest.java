package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.CANCELLED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.FORTALEZA;
import static br.com.castel.restaurant.domain.TabFixtures.INVALID_TAB_TRANSFER;
import static br.com.castel.restaurant.domain.TabFixtures.ORDERED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.OTHER_WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_ITEM_ALREADY_CANCELLED;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_ITEM_NOT_FOUND;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_ITEM_NOT_SERVICE_CHARGEABLE;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_NOT_OPEN;
import static br.com.castel.restaurant.domain.TabFixtures.TEN_PERCENT;
import static br.com.castel.restaurant.domain.TabFixtures.TRANSFERRED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.assertRejectedWith;
import static br.com.castel.restaurant.domain.TabFixtures.beer;
import static br.com.castel.restaurant.domain.TabFixtures.buffet;
import static br.com.castel.restaurant.domain.TabFixtures.cardTab;
import static br.com.castel.restaurant.domain.TabFixtures.closingTab;
import static br.com.castel.restaurant.domain.TabFixtures.grams;
import static br.com.castel.restaurant.domain.TabFixtures.order;
import static br.com.castel.restaurant.domain.TabFixtures.orderDish;
import static br.com.castel.restaurant.domain.TabFixtures.orderDishWithoutServiceCharge;
import static br.com.castel.restaurant.domain.TabFixtures.pizza;
import static br.com.castel.restaurant.domain.TabFixtures.stuffedCrust;
import static br.com.castel.restaurant.domain.TabFixtures.tabInStatus;
import static br.com.castel.restaurant.domain.TabFixtures.tableTab;
import static br.com.castel.restaurant.domain.TabFixtures.units;
import static br.com.castel.restaurant.domain.TabFixtures.withModifiers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.Money;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Moving whole lines from one tab to another, task 3.6 section 3: the order of the checks, all or
 * nothing, the money that has to be conserved and the trail each move leaves.
 */
class TabTransferTest {

    private final FakeTabBilling billing = new FakeTabBilling();

    /** An item of the given status, walked there through the transitions that reach it. */
    private static TabItem itemInStatus(Tab tab, TabItemStatus status) {
        TabItem item = orderDish(tab, "40.00");
        switch (status) {
            case PENDING -> {
            }
            case IN_PREPARATION -> tab.startItemPreparation(item.id(), ORDERED_AT);
            case READY -> tab.markItemReady(item.id(), ORDERED_AT);
            case DELIVERED -> tab.deliverItem(item.id(), ORDERED_AT);
            case CANCELLED -> tab.cancelItem(item.id(), "Cliente desistiu", WAITER, CANCELLED_AT);
        }
        return item;
    }

    @Nested
    @DisplayName("moving a line")
    class MovingALine {

        @Test
        void shouldHandTheWholeLineOverKeepingEverythingThatWasFrozenOnIt() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem item = orderDish(source, "40.00");
            source.startItemPreparation(item.id(), ORDERED_AT);

            source.transferItemsTo(destination, Set.of(item.id()), OTHER_WAITER, TRANSFERRED_AT);

            TabItem moved = destination.item(item.id());
            assertThat(moved.tabId()).isEqualTo(destination.id());
            assertThat(moved.lineTotal()).isEqualTo(Money.of("40.00"));
            assertThat(moved.status()).isEqualTo(TabItemStatus.IN_PREPARATION);
            assertThat(moved.preparationStartedAt()).contains(ORDERED_AT);
            assertThat(moved.serviceChargeable()).isTrue();
            assertThat(moved.orderedBy()).isEqualTo(WAITER);
            assertThat(moved.orderedAt()).isEqualTo(ORDERED_AT);
            assertThat(moved.transferredFromTabId()).contains(source.id());
            assertThat(moved.transferredBy()).contains(OTHER_WAITER);
            assertThat(moved.transferredAt()).contains(TRANSFERRED_AT);
            assertThat(source.items()).isEmpty();
        }

        @Test
        void shouldMoveOnlyTheLinesThatWereNamed() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem moving = orderDish(source, "40.00");
            TabItem staying = orderDish(source, "15.00");

            source.transferItemsTo(destination, Set.of(moving.id()), WAITER, TRANSFERRED_AT);

            assertThat(source.items()).extracting(TabItem::id).containsExactly(staying.id());
            assertThat(destination.items()).extracting(TabItem::id).containsExactly(moving.id());
            assertThat(staying.transferredFromTabId()).isEmpty();
        }

        @Test
        void shouldKeepTheSourceOpenHoldingItsTableWhenNoActiveItemIsLeft() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem item = orderDish(source, "40.00");

            source.transferItemsTo(destination, Set.of(item.id()), WAITER, TRANSFERRED_AT);

            assertThat(source.status()).isEqualTo(TabStatus.OPEN);
            assertThat(source.status().holdsItsPlace()).isTrue();
        }

        @Test
        void shouldPutTheMovedLineBackOnTheFirstSplitGroup() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem item = orderDish(source, "40.00");
            source.assignToSplitGroup(Map.of(item.id(), 5));

            source.transferItemsTo(destination, Set.of(item.id()), WAITER, TRANSFERRED_AT);

            assertThat(destination.item(item.id()).splitGroup()).isEqualTo(TabItem.DEFAULT_SPLIT_GROUP);
        }
    }

    @Nested
    @DisplayName("status of the two tabs")
    class StatusOfTheTwoTabs {

        @ParameterizedTest(name = "source {0}")
        @EnumSource(value = TabStatus.class, names = "OPEN", mode = EXCLUDE)
        void shouldRejectAMoveOutOfATabThatIsNotOpen(TabStatus status) {
            Tab source = tabInStatus(status, billing);
            Tab destination = tableTab();

            assertRejectedWith(
                    () -> source.transferItemsTo(destination, Set.of(TabItemId.newId()), WAITER, TRANSFERRED_AT),
                    TAB_NOT_OPEN);
            assertThat(destination.items()).isEmpty();
        }

        @ParameterizedTest(name = "destination {0}")
        @EnumSource(value = TabStatus.class, names = "OPEN", mode = EXCLUDE)
        void shouldRejectAMoveIntoATabThatIsNotOpen(TabStatus status) {
            Tab source = tableTab();
            TabItem item = orderDish(source, "40.00");
            Tab destination = tabInStatus(status, billing);

            assertRejectedWith(
                    () -> source.transferItemsTo(destination, Set.of(item.id()), WAITER, TRANSFERRED_AT),
                    TAB_NOT_OPEN);
            assertThat(source.item(item.id()).tabId()).isEqualTo(source.id());
        }

        @Test
        void shouldRejectMovingItemsToTheTabItself() {
            Tab tab = tableTab();
            TabItem item = orderDish(tab, "40.00");

            assertRejectedWith(
                    () -> tab.transferItemsTo(tab, Set.of(item.id()), WAITER, TRANSFERRED_AT),
                    INVALID_TAB_TRANSFER);
        }

        @Test
        void shouldRejectAnEmptyListOfItems() {
            Tab source = tableTab();

            assertRejectedWith(
                    () -> source.transferItemsTo(tableTab(), Set.of(), WAITER, TRANSFERRED_AT),
                    INVALID_TAB_TRANSFER);
        }

        @Test
        void shouldCheckTheDestinationIsAnotherTabBeforeItsStatus() {
            Tab tab = closingTab(billing);

            assertRejectedWith(
                    () -> tab.transferItemsTo(tab, Set.of(TabItemId.newId()), WAITER, TRANSFERRED_AT),
                    INVALID_TAB_TRANSFER);
        }

        @Test
        void shouldCheckTheListOfItemsBeforeTheStatusOfTheTabs() {
            Tab source = closingTab(billing);

            assertRejectedWith(
                    () -> source.transferItemsTo(tableTab(), Set.of(), WAITER, TRANSFERRED_AT),
                    INVALID_TAB_TRANSFER);
        }

        @Test
        void shouldCheckThatEveryItemExistsBeforeLookingAtItsStatus() {
            Tab source = tableTab();
            TabItem cancelled = itemInStatus(source, TabItemStatus.CANCELLED);

            assertRejectedWith(
                    () -> source.transferItemsTo(
                            tableTab(), Set.of(cancelled.id(), TabItemId.newId()), WAITER, TRANSFERRED_AT),
                    TAB_ITEM_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("status of the item")
    class StatusOfTheItem {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TabItemStatus.class, names = "CANCELLED", mode = EXCLUDE)
        void shouldMoveAnItemInAnyStatusButCancelled(TabItemStatus status) {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem item = itemInStatus(source, status);

            source.transferItemsTo(destination, Set.of(item.id()), WAITER, TRANSFERRED_AT);

            assertThat(destination.item(item.id()).status()).isEqualTo(status);
        }

        @Test
        void shouldRejectACancelledItemLeavingItWhereItWasCancelled() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem item = itemInStatus(source, TabItemStatus.CANCELLED);

            assertRejectedWith(
                    () -> source.transferItemsTo(destination, Set.of(item.id()), WAITER, TRANSFERRED_AT),
                    TAB_ITEM_ALREADY_CANCELLED);
            assertThat(source.item(item.id()).tabId()).isEqualTo(source.id());
            assertThat(destination.items()).isEmpty();
        }

        @Test
        void shouldMoveAPlateSoldByWeightThatIsBornDelivered() {
            Tab source = cardTab();
            Tab destination = tableTab();
            TabItem plate = source.addItem(buffet(), grams(500), WAITER, ORDERED_AT, FORTALEZA);

            source.transferItemsTo(destination, Set.of(plate.id()), WAITER, TRANSFERRED_AT);

            TabItem moved = destination.item(plate.id());
            assertThat(moved.status()).isEqualTo(TabItemStatus.DELIVERED);
            assertThat(moved.lineTotal()).isEqualTo(Money.of("29.95"));
        }

        @Test
        void shouldMoveNothingWhenOneOfTheNamedItemsIsNotOnTheSource() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem first = orderDish(source, "40.00");
            TabItem second = orderDish(source, "15.00");

            assertRejectedWith(
                    () -> source.transferItemsTo(
                            destination, Set.of(first.id(), second.id(), TabItemId.newId()), WAITER, TRANSFERRED_AT),
                    TAB_ITEM_NOT_FOUND);
            assertThat(source.subtotal()).isEqualTo(Money.of("55.00"));
            assertThat(destination.items()).isEmpty();
            assertThat(source.item(first.id()).transferredFromTabId()).isEmpty();
        }

        @Test
        void shouldMoveNothingWhenOneOfTheNamedItemsIsCancelled() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem active = orderDish(source, "40.00");
            TabItem cancelled = itemInStatus(source, TabItemStatus.CANCELLED);

            assertRejectedWith(
                    () -> source.transferItemsTo(
                            destination, Set.of(active.id(), cancelled.id()), WAITER, TRANSFERRED_AT),
                    TAB_ITEM_ALREADY_CANCELLED);
            assertThat(destination.items()).isEmpty();
            assertThat(source.item(active.id()).tabId()).isEqualTo(source.id());
        }
    }

    @Nested
    @DisplayName("money")
    class TheMoney {

        @Test
        void shouldConserveTheSumOfBothSubtotals() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem first = orderDish(source, "40.00");
            TabItem second = orderDish(source, "15.50");
            orderDish(source, "7.25");
            orderDish(destination, "33.35");
            Money before = source.subtotal().plus(destination.subtotal());

            source.transferItemsTo(destination, Set.of(first.id(), second.id()), WAITER, TRANSFERRED_AT);

            assertThat(source.subtotal().plus(destination.subtotal())).isEqualTo(before);
            assertThat(source.subtotal()).isEqualTo(Money.of("7.25"));
            assertThat(destination.subtotal()).isEqualTo(Money.of("88.85"));
        }

        @Test
        void shouldKeepTheLineTotalAndEveryModifierOfTheMovedLine() {
            MenuItem pizza = pizza();
            Modifier crust = stuffedCrust();
            pizza.offerModifier(crust, 2);
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem item = order(source, pizza, withModifiers(new ModifierChoice(crust, 1)));

            source.transferItemsTo(destination, Set.of(item.id()), WAITER, TRANSFERRED_AT);

            TabItem moved = destination.item(item.id());
            assertThat(moved.lineTotal()).isEqualTo(Money.of("70.50"));
            assertThat(moved.unitPrice()).contains(Money.of("62.00"));
            assertThat(moved.modifiers()).singleElement().satisfies(modifier -> {
                assertThat(modifier.modifierName()).isEqualTo("Borda recheada");
                assertThat(modifier.price()).isEqualTo(Money.of("8.50"));
                assertThat(modifier.quantity()).isEqualTo(1);
            });
        }

        @Test
        void shouldChargeServiceOnceOverTheSumAtTheDestinationAndNotPerItem() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem first = orderDish(source, "3.35");
            TabItem second = orderDish(source, "3.35");
            TabItem third = orderDish(source, "3.35");

            source.transferItemsTo(
                    destination, Set.of(first.id(), second.id(), third.id()), WAITER, TRANSFERRED_AT);

            assertThat(destination.serviceChargeBase()).isEqualTo(Money.of("10.05"));
            assertThat(destination.serviceCharge(TEN_PERCENT)).isEqualTo(Money.of("1.01"));
            assertThat(destination.total(TEN_PERCENT)).isEqualTo(Money.of("11.06"));
            assertThat(source.serviceCharge(TEN_PERCENT)).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldLeaveTheServiceChargeBaseAtZeroWhenTheLineGoesFromATableToACard() {
            Tab source = tableTab();
            Tab destination = cardTab();
            TabItem item = orderDish(source, "40.00");

            source.transferItemsTo(destination, Set.of(item.id()), WAITER, TRANSFERRED_AT);

            assertThat(destination.serviceChargeApplied()).isFalse();
            assertThat(destination.serviceChargeBase()).isEqualTo(Money.ZERO);
            assertThat(destination.total(TEN_PERCENT)).isEqualTo(Money.of("40.00"));
        }

        @Test
        void shouldKeepALineBornOnACardOutOfTheServiceChargeWhenItReachesATable() {
            Tab source = cardTab();
            Tab destination = tableTab();
            TabItem item = orderDish(source, "40.00");

            source.transferItemsTo(destination, Set.of(item.id()), WAITER, TRANSFERRED_AT);

            assertThat(destination.item(item.id()).serviceChargeable()).isFalse();
            assertThat(destination.serviceChargeBase()).isEqualTo(Money.ZERO);
            assertRejectedWith(
                    () -> destination.restoreServiceChargeTo(item.id()), TAB_ITEM_NOT_SERVICE_CHARGEABLE);
        }

        @Test
        void shouldWaiveTheChargeOnArrivalWhenTheSourceHasItTurnedOff() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem item = orderDish(source, "40.00");
            source.removeServiceCharge();

            source.transferItemsTo(destination, Set.of(item.id()), WAITER, TRANSFERRED_AT);

            assertThat(destination.item(item.id()).serviceChargeWaived()).isTrue();
            assertThat(destination.serviceChargeApplied()).isTrue();
            assertThat(destination.serviceChargeBase()).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldNotWaiveALineThatNeverCarriedTheChargeWhenTheSourceHasItTurnedOff() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem item = orderDishWithoutServiceCharge(source, "40.00");
            source.removeServiceCharge();

            source.transferItemsTo(destination, Set.of(item.id()), WAITER, TRANSFERRED_AT);

            assertThat(destination.item(item.id()).serviceChargeWaived()).isFalse();
        }

        @Test
        void shouldKeepALineWaivedByTheOperatorWaivedAtADestinationThatCharges() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem item = orderDish(source, "40.00");
            source.removeServiceChargeFrom(item.id());

            source.transferItemsTo(destination, Set.of(item.id()), WAITER, TRANSFERRED_AT);

            assertThat(destination.item(item.id()).serviceChargeWaived()).isTrue();
            assertThat(destination.serviceChargeBase()).isEqualTo(Money.ZERO);
        }
    }

    @Nested
    @DisplayName("trail and events")
    class TrailAndEvents {

        @Test
        void shouldRecordOneTrailRowPerItemMovedWithBothTabsTheKindTheAuthorAndTheMoment() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem first = orderDish(source, "40.00");
            TabItem second = orderDish(source, "15.00");

            TabTransferResult result =
                    source.transferItemsTo(destination, Set.of(first.id(), second.id()), OTHER_WAITER, TRANSFERRED_AT);

            assertThat(result.movedItems()).isEqualTo(2);
            assertThat(result.transfers())
                    .extracting(TabItemTransfer::tabItemId)
                    .containsExactlyInAnyOrder(first.id(), second.id());
            assertThat(result.transfers()).allSatisfy(transfer -> {
                assertThat(transfer.fromTabId()).isEqualTo(source.id());
                assertThat(transfer.toTabId()).isEqualTo(destination.id());
                assertThat(transfer.kind()).isEqualTo(TabTransferKind.TRANSFER);
                assertThat(transfer.transferredBy()).isEqualTo(OTHER_WAITER);
                assertThat(transfer.transferredAt()).isEqualTo(TRANSFERRED_AT);
                assertThat(transfer.id()).isNotNull();
            });
        }

        @Test
        void shouldKeepOneTrailRowPerHopAndPointTheShortcutAtTheLastOne() {
            Tab first = tableTab();
            Tab second = tableTab();
            Tab third = tableTab();
            TabItem item = orderDish(first, "40.00");

            TabItemTransfer firstHop = first.transferItemsTo(second, Set.of(item.id()), WAITER, TRANSFERRED_AT)
                    .transfers()
                    .get(0);
            TabItemTransfer secondHop = second.transferItemsTo(
                            third, Set.of(item.id()), OTHER_WAITER, TRANSFERRED_AT.plusSeconds(60))
                    .transfers()
                    .get(0);

            assertThat(firstHop.fromTabId()).isEqualTo(first.id());
            assertThat(firstHop.toTabId()).isEqualTo(second.id());
            assertThat(secondHop.fromTabId()).isEqualTo(second.id());
            assertThat(secondHop.toTabId()).isEqualTo(third.id());
            assertThat(secondHop.id()).isNotEqualTo(firstHop.id());
            assertThat(third.item(item.id()).transferredFromTabId()).contains(second.id());
            assertThat(third.item(item.id()).transferredBy()).contains(OTHER_WAITER);
        }

        @Test
        void shouldPublishOneEventPerItemMovedCarryingBothTabsAndTheStationOfTheItem() {
            Tab source = tableTab();
            Tab destination = tableTab();
            TabItem dish = orderDish(source, "40.00");
            TabItem beer = order(source, beer(), units(1));

            List<TabItemTransferred> events =
                    source.transferItemsTo(destination, Set.of(dish.id(), beer.id()), WAITER, TRANSFERRED_AT)
                            .events();

            assertThat(events).hasSize(2);
            assertThat(events).allSatisfy(event -> {
                assertThat(event.fromTabId()).isEqualTo(source.id());
                assertThat(event.toTabId()).isEqualTo(destination.id());
                assertThat(event.occurredAt()).isEqualTo(TRANSFERRED_AT);
            });
            assertThat(events)
                    .extracting(TabItemTransferred::itemId)
                    .containsExactlyInAnyOrder(dish.id(), beer.id());
            assertThat(events)
                    .extracting(TabItemTransferred::station)
                    .containsExactlyInAnyOrder(PrepStation.KITCHEN, PrepStation.BAR);
        }

        @Test
        void shouldAnnounceAReadyItemToTheWaitersAsWell() {
            Tab source = tableTab();
            TabItem item = itemInStatus(source, TabItemStatus.READY);

            TabItemTransferred event = source.transferItemsTo(tableTab(), Set.of(item.id()), WAITER, TRANSFERRED_AT)
                    .events()
                    .get(0);

            assertThat(event.reachesKitchenQueue()).isTrue();
            assertThat(event.isReady()).isTrue();
        }

        @Test
        void shouldKeepADeliveredItemOffTheKitchenQueue() {
            Tab source = tableTab();
            TabItem item = itemInStatus(source, TabItemStatus.DELIVERED);

            TabItemTransferred event = source.transferItemsTo(tableTab(), Set.of(item.id()), WAITER, TRANSFERRED_AT)
                    .events()
                    .get(0);

            assertThat(event.reachesKitchenQueue()).isFalse();
            assertThat(event.isReady()).isFalse();
        }
    }
}
