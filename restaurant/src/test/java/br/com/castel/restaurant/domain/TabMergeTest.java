package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.FakeTabBilling.FOLIO_BALANCE_NOT_ZERO;
import static br.com.castel.restaurant.domain.TabFixtures.CANCELLED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.CLOSING_AT;
import static br.com.castel.restaurant.domain.TabFixtures.FORTALEZA;
import static br.com.castel.restaurant.domain.TabFixtures.INACTIVE_DINING_TABLE;
import static br.com.castel.restaurant.domain.TabFixtures.INVALID_TAB_MERGE;
import static br.com.castel.restaurant.domain.TabFixtures.MERGED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.ORDERED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.OTHER_WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_ITEM_ALREADY_CANCELLED;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_NOT_CLOSING;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_NOT_OPEN;
import static br.com.castel.restaurant.domain.TabFixtures.TEN_PERCENT;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.activeTable;
import static br.com.castel.restaurant.domain.TabFixtures.assertRejectedWith;
import static br.com.castel.restaurant.domain.TabFixtures.cardTab;
import static br.com.castel.restaurant.domain.TabFixtures.closingTab;
import static br.com.castel.restaurant.domain.TabFixtures.dish;
import static br.com.castel.restaurant.domain.TabFixtures.mergedTab;
import static br.com.castel.restaurant.domain.TabFixtures.oneUnit;
import static br.com.castel.restaurant.domain.TabFixtures.orderDish;
import static br.com.castel.restaurant.domain.TabFixtures.tabInStatus;
import static br.com.castel.restaurant.domain.TabFixtures.tableTab;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.sharedkernel.Money;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Absorbing one tab into another and moving a tab to another table, task 3.6 section 3, plus the
 * finality of {@code MERGED}: the status that answers no to every operation of the tab.
 */
class TabMergeTest {

    private static final String REOPENING_REASON = "Cliente pediu sobremesa";

    private final FakeTabBilling billing = new FakeTabBilling();

    /** A tab reopened after closing, so it carries a folio with the balance left by its payments. */
    private Tab reopenedTab(String paid) {
        Tab tab = closingTab(billing);
        if (paid != null) {
            tab.receivePayment(PaymentMethod.PIX, Money.of(paid), "key-" + paid, billing);
        }
        tab.reopen(REOPENING_REASON, billing);
        return tab;
    }

    @Nested
    @DisplayName("absorbing another tab")
    class AbsorbingAnotherTab {

        @Test
        void shouldTakeEveryActiveItemAndLeaveTheAbsorbedTabMerged() {
            Tab receiving = tableTab();
            Tab absorbed = tableTab();
            TabItem first = orderDish(absorbed, "40.00");
            TabItem second = orderDish(absorbed, "15.00");

            receiving.mergeWith(absorbed, billing, OTHER_WAITER, MERGED_AT);

            assertThat(receiving.items()).extracting(TabItem::id).containsExactlyInAnyOrder(first.id(), second.id());
            assertThat(receiving.item(first.id()).transferredFromTabId()).contains(absorbed.id());
            assertThat(absorbed.status()).isEqualTo(TabStatus.MERGED);
            assertThat(absorbed.mergedIntoTabId()).contains(receiving.id());
            assertThat(absorbed.mergedAt()).contains(MERGED_AT);
            assertThat(absorbed.mergedBy()).contains(OTHER_WAITER);
            assertThat(absorbed.status().holdsItsPlace()).isFalse();
            assertThat(absorbed.items()).isEmpty();
        }

        @Test
        void shouldConserveTheMoneyOfBothTabs() {
            Tab receiving = tableTab();
            Tab absorbed = tableTab();
            orderDish(receiving, "33.35");
            orderDish(absorbed, "40.00");
            orderDish(absorbed, "15.50");
            Money before = receiving.subtotal().plus(absorbed.subtotal());

            receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT);

            assertThat(receiving.subtotal()).isEqualTo(before);
            assertThat(absorbed.subtotal()).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldLeaveCancelledItemsOnTheTabWhereTheyWereCancelled() {
            Tab receiving = tableTab();
            Tab absorbed = tableTab();
            TabItem active = orderDish(absorbed, "40.00");
            TabItem cancelled = orderDish(absorbed, "15.00");
            absorbed.cancelItem(cancelled.id(), "Cliente desistiu", WAITER, CANCELLED_AT);

            TabTransferResult result = receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT);

            assertThat(result.movedItems()).isEqualTo(1);
            assertThat(receiving.items()).extracting(TabItem::id).containsExactly(active.id());
            assertThat(absorbed.items()).extracting(TabItem::id).containsExactly(cancelled.id());
            assertThat(absorbed.item(cancelled.id()).cancellationReason()).contains("Cliente desistiu");
        }

        @Test
        void shouldAcceptATabWithNoActiveItemAtAll() {
            Tab receiving = tableTab();
            Tab absorbed = tableTab();

            TabTransferResult result = receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT);

            assertThat(result.movedItems()).isZero();
            assertThat(result.events()).isEmpty();
            assertThat(absorbed.status()).isEqualTo(TabStatus.MERGED);
        }

        @Test
        void shouldRecordTheTrailOfTheMergeWithOneRowPerActiveItem() {
            Tab receiving = tableTab();
            Tab absorbed = tableTab();
            TabItem first = orderDish(absorbed, "40.00");
            TabItem second = orderDish(absorbed, "15.00");

            TabTransferResult result = receiving.mergeWith(absorbed, billing, OTHER_WAITER, MERGED_AT);

            assertThat(result.transfers())
                    .extracting(TabItemTransfer::tabItemId)
                    .containsExactlyInAnyOrder(first.id(), second.id());
            assertThat(result.transfers()).allSatisfy(transfer -> {
                assertThat(transfer.kind()).isEqualTo(TabTransferKind.MERGE);
                assertThat(transfer.fromTabId()).isEqualTo(absorbed.id());
                assertThat(transfer.toTabId()).isEqualTo(receiving.id());
                assertThat(transfer.transferredBy()).isEqualTo(OTHER_WAITER);
                assertThat(transfer.transferredAt()).isEqualTo(MERGED_AT);
            });
            assertThat(result.events()).extracting(TabItemTransferred::toTabId).containsOnly(receiving.id());
        }

        @Test
        void shouldKeepTheChargeOfTheReceivingTabAndWaiveTheItemsOfATabThatHadItOff() {
            Tab receiving = tableTab();
            Tab absorbed = tableTab();
            TabItem item = orderDish(absorbed, "40.00");
            absorbed.removeServiceCharge();

            receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT);

            assertThat(receiving.serviceChargeApplied()).isTrue();
            assertThat(receiving.item(item.id()).serviceChargeWaived()).isTrue();
            assertThat(receiving.serviceCharge(TEN_PERCENT)).isEqualTo(Money.ZERO);
        }
    }

    @Nested
    @DisplayName("number of guests")
    class NumberOfGuests {

        @Test
        void shouldAddTheGuestsUpWhenBothTabsHaveANumber() {
            Tab receiving = tableTab();
            Tab absorbed = tableTab();
            receiving.recordGuestCount(3);
            absorbed.recordGuestCount(2);

            receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT);

            assertThat(receiving.guestCount()).contains(5);
        }

        @Test
        void shouldKeepTheReceivingTabWithoutGuestsWhenOnlyTheAbsorbedOneHasANumber() {
            Tab receiving = tableTab();
            Tab absorbed = tableTab();
            absorbed.recordGuestCount(2);

            receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT);

            assertThat(receiving.guestCount()).isEmpty();
        }

        @Test
        void shouldKeepTheGuestsOfTheReceivingTabWhenTheAbsorbedOneHasNone() {
            Tab receiving = tableTab();
            Tab absorbed = tableTab();
            receiving.recordGuestCount(3);

            receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT);

            assertThat(receiving.guestCount()).contains(3);
        }
    }

    @Nested
    @DisplayName("status of the two tabs")
    class StatusOfTheTwoTabs {

        @ParameterizedTest(name = "receiving {0}")
        @EnumSource(value = TabStatus.class, names = "OPEN", mode = EXCLUDE)
        void shouldRejectAMergeIntoATabThatIsNotOpen(TabStatus status) {
            Tab receiving = tabInStatus(status, billing);
            Tab absorbed = tableTab();
            TabItem item = orderDish(absorbed, "40.00");

            assertRejectedWith(() -> receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT), TAB_NOT_OPEN);
            assertThat(absorbed.status()).isEqualTo(TabStatus.OPEN);
            assertThat(absorbed.item(item.id()).tabId()).isEqualTo(absorbed.id());
        }

        @ParameterizedTest(name = "absorbed {0}")
        @EnumSource(value = TabStatus.class, names = "OPEN", mode = EXCLUDE)
        void shouldRejectAbsorbingATabThatIsNotOpen(TabStatus status) {
            Tab receiving = tableTab();
            Tab absorbed = tabInStatus(status, billing);

            assertRejectedWith(() -> receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT), TAB_NOT_OPEN);
            assertThat(receiving.items()).isEmpty();
        }

        @Test
        void shouldRejectMergingATabIntoItself() {
            Tab tab = tableTab();
            orderDish(tab, "40.00");

            assertRejectedWith(() -> tab.mergeWith(tab, billing, WAITER, MERGED_AT), INVALID_TAB_MERGE);
            assertThat(tab.status()).isEqualTo(TabStatus.OPEN);
        }

        @Test
        void shouldCheckTheTabIsAnotherOneBeforeItsStatus() {
            Tab tab = closingTab(billing);

            assertRejectedWith(() -> tab.mergeWith(tab, billing, WAITER, MERGED_AT), INVALID_TAB_MERGE);
        }

        @Test
        void shouldRejectMergingBackIntoATabAlreadyAbsorbedSoNoCycleIsFormed() {
            Tab receiving = tableTab();
            Tab absorbed = tableTab();
            receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT);

            assertRejectedWith(() -> absorbed.mergeWith(receiving, billing, WAITER, MERGED_AT), TAB_NOT_OPEN);
            assertRejectedWith(() -> receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT), TAB_NOT_OPEN);
            assertThat(absorbed.mergedIntoTabId()).contains(receiving.id());
            assertThat(receiving.mergedIntoTabId()).isEmpty();
        }
    }

    @Nested
    @DisplayName("folio of the absorbed tab")
    class FolioOfTheAbsorbedTab {

        @Test
        void shouldCloseTheFolioOfTheAbsorbedTabWhenItsBalanceIsZero() {
            Tab receiving = tableTab();
            Tab absorbed = reopenedTab(null);

            receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT);

            assertThat(billing.closedFolios).contains(absorbed.folioId().orElseThrow());
            assertThat(absorbed.status()).isEqualTo(TabStatus.MERGED);
            assertThat(receiving.subtotal()).isEqualTo(Money.of("100.00"));
        }

        @Test
        void shouldRejectTheMergeAndChangeNothingWhenTheAbsorbedFolioStillHasABalance() {
            Tab receiving = tableTab();
            Tab absorbed = reopenedTab("50.00");

            assertRejectedWith(
                    () -> receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT), FOLIO_BALANCE_NOT_ZERO);
            assertThat(absorbed.status()).isEqualTo(TabStatus.OPEN);
            assertThat(absorbed.subtotal()).isEqualTo(Money.of("100.00"));
            assertThat(absorbed.mergedIntoTabId()).isEmpty();
            assertThat(receiving.items()).isEmpty();
            assertThat(billing.closedFolios).isEmpty();
        }

    }

    @Nested
    @DisplayName("a merged tab is final")
    class AMergedTabIsFinal {

        @Test
        void shouldRefuseNewItems() {
            Tab tab = mergedTab(billing);

            assertRejectedWith(() -> tab.addItem(dish("10.00"), oneUnit(), WAITER, ORDERED_AT, FORTALEZA),
                    TAB_NOT_OPEN);
        }

        @Test
        void shouldRefuseCancellingAnItemThatStayedOnIt() {
            Tab receiving = tableTab();
            Tab absorbed = tableTab();
            TabItem cancelled = orderDish(absorbed, "40.00");
            absorbed.cancelItem(cancelled.id(), "Cliente desistiu", WAITER, CANCELLED_AT);
            receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT);

            assertRejectedWith(
                    () -> absorbed.cancelItem(cancelled.id(), "De novo", WAITER, CANCELLED_AT), TAB_NOT_OPEN);
        }

        @Test
        void shouldRefuseBeingCancelled() {
            Tab tab = mergedTab(billing);

            assertRejectedWith(() -> tab.cancel("Mesa errada", billing, WAITER, CANCELLED_AT), TAB_NOT_OPEN);
            assertThat(tab.status()).isEqualTo(TabStatus.MERGED);
        }

        @Test
        void shouldRefuseChangingTheServiceCharge() {
            Tab tab = mergedTab(billing);

            assertRejectedWith(tab::removeServiceCharge, TAB_NOT_OPEN);
            assertRejectedWith(tab::restoreServiceCharge, TAB_NOT_OPEN);
        }

        @Test
        void shouldRefuseChangingTheSplitGroupsAndTheNumberOfGuests() {
            Tab tab = mergedTab(billing);

            assertRejectedWith(() -> tab.assignToSplitGroup(Map.of(TabItemId.newId(), 2)), TAB_NOT_OPEN);
            assertRejectedWith(() -> tab.recordGuestCount(4), TAB_NOT_OPEN);
        }

        @Test
        void shouldRefuseStartingToCloseAndBeingReopenedPaidOrClosed() {
            Tab tab = mergedTab(billing);

            assertRejectedWith(() -> tab.startClosing(TEN_PERCENT, billing, WAITER, CLOSING_AT), TAB_NOT_OPEN);
            assertRejectedWith(() -> tab.reopen(REOPENING_REASON, billing), TAB_NOT_CLOSING);
            assertRejectedWith(
                    () -> tab.receivePayment(PaymentMethod.CASH, Money.of("10.00"), "key-merged", billing),
                    TAB_NOT_CLOSING);
            assertRejectedWith(() -> tab.close(billing, WAITER, CLOSING_AT), TAB_NOT_CLOSING);
        }

        @Test
        void shouldHaveNoActiveItemLeftForTheKitchenToAdvance() {
            Tab receiving = tableTab();
            Tab absorbed = tableTab();
            TabItem active = orderDish(absorbed, "40.00");
            TabItem cancelled = orderDish(absorbed, "15.00");
            absorbed.cancelItem(cancelled.id(), "Cliente desistiu", WAITER, CANCELLED_AT);
            receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT);

            assertThat(absorbed.items()).noneMatch(TabItem::isActive);
            assertRejectedWith(
                    () -> absorbed.startItemPreparation(cancelled.id(), MERGED_AT), TAB_ITEM_ALREADY_CANCELLED);
            assertThat(receiving.item(active.id()).status()).isEqualTo(TabItemStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("moving to another table")
    class MovingToAnotherTable {

        @Test
        void shouldOpenANewTabOnTheDestinationTableWithTheItems() {
            Tab current = tableTab();
            TabItem item = orderDish(current, "40.00");
            DiningTable destinationTable = activeTable();

            TabMove move = current.moveToTable(destinationTable, billing, OTHER_WAITER, MERGED_AT);

            Tab newTab = move.newTab();
            assertThat(newTab.id()).isNotEqualTo(current.id());
            assertThat(newTab.status()).isEqualTo(TabStatus.OPEN);
            assertThat(newTab.diningTableId()).contains(destinationTable.id());
            assertThat(newTab.items()).extracting(TabItem::id).containsExactly(item.id());
            assertThat(newTab.subtotal()).isEqualTo(Money.of("40.00"));
        }

        /** Decision T17: the same party at another table keeps its number of guests. */
        @Test
        void shouldCarryTheNumberOfGuestsToTheNewTable() {
            Tab current = tableTab();
            orderDish(current, "40.00");
            current.recordGuestCount(3);

            TabMove move = current.moveToTable(activeTable(), billing, WAITER, MERGED_AT);

            assertThat(move.newTab().guestCount()).contains(3);
        }

        /**
         * Decision T18: the split the operator built survives a change of table. The reason decision
         * T9 sends a transferred line back to group 1 — group 2 of table 4 is not group 2 of table 5 —
         * does not exist here: there is one single party, and the new tab has no other group.
         */
        @Test
        void shouldCarryTheSplitOfEachLineToTheNewTable() {
            Tab current = tableTab();
            TabItem couple = orderDish(current, "40.00");
            TabItem friend = orderDish(current, "30.00");
            current.assignToSplitGroup(Map.of(couple.id(), 1, friend.id(), 3));

            TabMove move = current.moveToTable(activeTable(), billing, WAITER, MERGED_AT);

            Tab newTab = move.newTab();
            assertThat(newTab.item(couple.id()).splitGroup()).isEqualTo(1);
            assertThat(newTab.item(friend.id()).splitGroup()).isEqualTo(3);
        }

        /**
         * Decision T18: a tab on a table is born charging service, so a new one would make the charge
         * the operator took off reappear by itself on the next item ordered — which is what decision
         * T7 exists to prevent on a single item.
         */
        @Test
        void shouldCarryTheServiceChargeTheOperatorTurnedOffToTheNewTable() {
            Tab current = tableTab();
            orderDish(current, "100.00");
            current.removeServiceCharge();

            Tab newTab = current.moveToTable(activeTable(), billing, WAITER, MERGED_AT).newTab();

            assertThat(newTab.serviceChargeApplied()).isFalse();
            assertThat(newTab.total(TEN_PERCENT)).isEqualTo(Money.of("100.00"));
            newTab.addItem(dish("10.00"), oneUnit(), WAITER, ORDERED_AT, FORTALEZA);
            assertThat(newTab.total(TEN_PERCENT))
                    .as("the charge the operator took off does not come back on the next item")
                    .isEqualTo(Money.of("110.00"));
        }

        /**
         * Decision T21: the moment the party sat down is theirs, not the moment of the move. The
         * floor list orders by it and shows it, so taking the moment of the move would say "table 7,
         * open just now" about people who have been there two hours. Who moved the tab is on the old
         * one, in {@code mergedBy}.
         */
        @Test
        void shouldCarryTheMomentThePartySatDownToTheNewTable() {
            Tab current = tableTab();
            orderDish(current, "40.00");

            TabMove move = current.moveToTable(activeTable(), billing, OTHER_WAITER, MERGED_AT);

            Tab newTab = move.newTab();
            assertThat(newTab.openedAt()).isEqualTo(current.openedAt());
            assertThat(newTab.openedBy()).isEqualTo(current.openedBy());
            assertThat(current.mergedBy())
                    .as("who moved the tab is recorded on the tab that was left behind")
                    .contains(OTHER_WAITER);
        }

        /** A real merge is two parties: there the receiving tab's own choices rule (T9, T18). */
        @Test
        void shouldSendAMergedLineBackToTheFirstSplitGroup() {
            Tab receiving = tableTab();
            Tab absorbed = tableTab();
            TabItem item = orderDish(absorbed, "40.00");
            absorbed.assignToSplitGroup(Map.of(item.id(), 4));

            receiving.mergeWith(absorbed, billing, WAITER, MERGED_AT);

            assertThat(receiving.item(item.id()).splitGroup()).isEqualTo(1);
        }

        @Test
        void shouldLeaveTheOldTabMergedPointingAtTheNewOne() {
            Tab current = tableTab();
            orderDish(current, "40.00");

            TabMove move = current.moveToTable(activeTable(), billing, OTHER_WAITER, MERGED_AT);

            assertThat(current.status()).isEqualTo(TabStatus.MERGED);
            assertThat(current.mergedIntoTabId()).contains(move.newTab().id());
            assertThat(current.mergedAt()).contains(MERGED_AT);
            assertThat(current.mergedBy()).contains(OTHER_WAITER);
            assertThat(current.items()).isEmpty();
        }

        @Test
        void shouldRecordTheTrailOfAMoveWithItsOwnKind() {
            Tab current = tableTab();
            TabItem item = orderDish(current, "40.00");

            TabMove move = current.moveToTable(activeTable(), billing, WAITER, MERGED_AT);

            assertThat(move.result().transfers()).singleElement().satisfies(transfer -> {
                assertThat(transfer.kind()).isEqualTo(TabTransferKind.MOVE);
                assertThat(transfer.tabItemId()).isEqualTo(item.id());
                assertThat(transfer.fromTabId()).isEqualTo(current.id());
                assertThat(transfer.toTabId()).isEqualTo(move.newTab().id());
            });
            assertThat(move.result().events()).hasSize(1);
            assertThat(move.newTab().item(item.id()).transferredFromTabId()).contains(current.id());
        }

        @Test
        void shouldMoveASelfServiceCardToATableKeepingTheItemsOutOfTheServiceCharge() {
            Tab card = cardTab();
            TabItem item = card.addItem(dish("40.00"), oneUnit(), WAITER, ORDERED_AT, FORTALEZA);

            TabMove move = card.moveToTable(activeTable(), billing, WAITER, MERGED_AT);

            Tab newTab = move.newTab();
            assertThat(newTab.origin()).isEqualTo(TabOrigin.TABLE_SERVICE);
            assertThat(newTab.item(item.id()).serviceChargeable()).isFalse();
            assertThat(newTab.serviceChargeBase()).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldRejectADeactivatedDestinationTableLeavingTheTabWhereItIs() {
            Tab current = tableTab();
            orderDish(current, "40.00");
            DiningTable deactivated = activeTable();
            deactivated.deactivate();

            assertRejectedWith(
                    () -> current.moveToTable(deactivated, billing, WAITER, MERGED_AT), INACTIVE_DINING_TABLE);
            assertThat(current.status()).isEqualTo(TabStatus.OPEN);
            assertThat(current.mergedIntoTabId()).isEmpty();
        }

        @Test
        void shouldRejectMovingATabThatIsNotOpen() {
            Tab closing = closingTab(billing);

            assertRejectedWith(() -> closing.moveToTable(activeTable(), billing, WAITER, MERGED_AT), TAB_NOT_OPEN);
            assertThat(closing.status()).isEqualTo(TabStatus.CLOSING);
        }

    }
}
