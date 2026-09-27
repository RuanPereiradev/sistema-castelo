package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.CANCELLED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.INVALID_GUEST_COUNT;
import static br.com.castel.restaurant.domain.TabFixtures.INVALID_SPLIT_GROUP;
import static br.com.castel.restaurant.domain.TabFixtures.INVALID_SPLIT_PARTS;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_ITEM_NOT_FOUND;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_NOT_OPEN;
import static br.com.castel.restaurant.domain.TabFixtures.TEN_PERCENT;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.assertRejectedWith;
import static br.com.castel.restaurant.domain.TabFixtures.cancelledTab;
import static br.com.castel.restaurant.domain.TabFixtures.closedTab;
import static br.com.castel.restaurant.domain.TabFixtures.closingTab;
import static br.com.castel.restaurant.domain.TabFixtures.orderDish;
import static br.com.castel.restaurant.domain.TabFixtures.orderDishWithoutServiceCharge;
import static br.com.castel.restaurant.domain.TabFixtures.tableTab;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.sharedkernel.Money;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Splitting the bill of a tab, task 3.2 invariants 7 to 10. */
class TabSplitBillTest {

    private static List<Money> money(String... amounts) {
        return Arrays.stream(amounts).map(Money::of).toList();
    }

    private static Money sum(List<Money> amounts) {
        return amounts.stream().reduce(Money.ZERO, Money::plus);
    }

    @Nested
    @DisplayName("even split")
    class EvenSplit {

        @Test
        void shouldGiveTheLeftoverCentToTheFirstShares() {
            assertThat(TabBill.evenSplit(Money.of("100.00"), 3)).containsExactlyElementsOf(money("33.34", "33.33", "33.33"));
        }

        @Test
        void shouldSplitAnExactAmountInEqualShares() {
            assertThat(TabBill.evenSplit(Money.of("10.00"), 4)).containsExactlyElementsOf(money("2.50", "2.50", "2.50", "2.50"));
        }

        @Test
        void shouldSplitOneCentPerPartWhenPartsEqualTheCents() {
            assertThat(TabBill.evenSplit(Money.of("0.03"), 3)).containsExactlyElementsOf(money("0.01", "0.01", "0.01"));
        }

        @Test
        void shouldRefuseMorePartsThanCentsSoNoShareIsZero() {
            assertRejectedWith(() -> TabBill.evenSplit(Money.of("0.02"), 3), INVALID_SPLIT_PARTS);
        }

        @Test
        void shouldKeepTheWholeAmountInASinglePart() {
            assertThat(TabBill.evenSplit(Money.of("47.19"), 1)).containsExactly(Money.of("47.19"));
        }

        @Test
        void shouldAcceptTheMaximumOfNinetyNineParts() {
            List<Money> shares = TabBill.evenSplit(Money.of("100.00"), 99);

            // 10000 cents / 99 = 101 remainder 1
            assertThat(shares).hasSize(99);
            assertThat(shares.get(0)).isEqualTo(Money.of("1.02"));
            assertThat(shares.subList(1, 99)).containsOnly(Money.of("1.01"));
        }

        @ParameterizedTest(name = "{0} parts")
        @CsvSource({"100", "0", "-1"})
        void shouldRefusePartsOutsideOneToNinetyNine(int parts) {
            assertRejectedWith(() -> TabBill.evenSplit(Money.of("100.00"), parts), INVALID_SPLIT_PARTS);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"0.00", "-10.00"})
        void shouldRefuseAnAmountThatIsNotPositive(String amount) {
            assertRejectedWith(() -> TabBill.evenSplit(Money.of(amount), 1), INVALID_SPLIT_PARTS);
        }

        @Test
        void shouldAddUpToTheAmountWithSharesAtMostOneCentApart() {
            List<Money> shares = TabBill.evenSplit(Money.of("100.00"), 7);

            assertThat(sum(shares)).isEqualTo(Money.of("100.00"));
            assertThat(shares).containsExactlyElementsOf(
                    money("14.29", "14.29", "14.29", "14.29", "14.28", "14.28", "14.28"));
        }

        @Test
        void shouldSplitTheTotalOfTheTab() {
            Tab tab = tableTab();
            orderDish(tab, "90.00");

            // 90.00 + 9.00 = 99.00
            assertThat(tab.evenSplit(TEN_PERCENT, 2)).containsExactlyElementsOf(money("49.50", "49.50"));
            assertThat(tab.bill(TEN_PERCENT).evenSplit(4)).containsExactlyElementsOf(money("24.75", "24.75", "24.75", "24.75"));
        }

        @Test
        void shouldSplitTheTotalOfOneGroup() {
            Tab tab = tableTab();
            orderDish(tab, "50.00");
            TabItem other = orderDish(tab, "10.00");
            tab.assignToSplitGroup(Map.of(other.id(), 2));

            // group 2: 10.00 + 1.00
            assertThat(tab.bill(TEN_PERCENT).group(2).evenSplit(3)).containsExactlyElementsOf(money("3.67", "3.67", "3.66"));
        }
    }

    @Nested
    @DisplayName("groups of the bill")
    class Groups {

        @Test
        void shouldPutEveryItemInGroupOneByDefault() {
            Tab tab = tableTab();
            TabItem item = orderDish(tab, "100.00");

            TabBill bill = tab.bill(TEN_PERCENT);

            assertThat(item.splitGroup()).isEqualTo(1);
            assertThat(bill.groups()).singleElement().satisfies(group -> {
                assertThat(group.splitGroup()).isEqualTo(1);
                assertThat(group.total()).isEqualTo(Money.of("110.00"));
            });
        }

        @Test
        void shouldGiveATiedLeftoverCentToTheLowestGroup() {
            Tab tab = tableTab();
            orderDish(tab, "10.05");
            TabItem second = orderDish(tab, "10.05");
            tab.assignToSplitGroup(Map.of(second.id(), 2));

            TabBill bill = tab.bill(TEN_PERCENT);

            // 20.10 x 10% = 2.01; each group 1.005
            assertThat(bill.serviceCharge()).isEqualTo(Money.of("2.01"));
            assertThat(bill.group(1).serviceCharge()).isEqualTo(Money.of("1.01"));
            assertThat(bill.group(2).serviceCharge()).isEqualTo(Money.of("1.00"));
        }

        @Test
        void shouldGiveTheLeftoverCentsToTheLargestRemaindersAheadOfTheLowestGroup() {
            Tab tab = threeGroups("0.66", "0.67", "0.67");

            TabBill bill = tab.bill(TEN_PERCENT);

            // S = 0.20; exact shares 6.6, 6.7, 6.7 cents: floors 18, two cents to groups 2 and 3
            assertThat(bill.groups()).extracting(TabBill.SplitGroup::serviceCharge)
                    .containsExactlyElementsOf(money("0.06", "0.07", "0.07"));
        }

        @Test
        void shouldGiveOneLeftoverCentToTheSingleLargestRemainder() {
            Tab tab = threeGroups("3.33", "3.33", "3.34");

            TabBill bill = tab.bill(TEN_PERCENT);

            // S = 1.00; group by group rounding would give 0.99
            assertThat(bill.groups()).extracting(TabBill.SplitGroup::serviceCharge)
                    .containsExactlyElementsOf(money("0.33", "0.33", "0.34"));
        }

        @Test
        void shouldBreakATieAmongThreeGroupsTowardTheLowestNumbers() {
            Tab tab = threeGroups("0.05", "0.05", "0.05");

            TabBill bill = tab.bill(TEN_PERCENT);

            // S = 0.015 -> 0.02; each exact share 0.667 cents
            assertThat(bill.groups()).extracting(TabBill.SplitGroup::serviceCharge)
                    .containsExactlyElementsOf(money("0.01", "0.01", "0.00"));
        }

        @Test
        void shouldAlwaysAddTheGroupsUpToTheTab() {
            Tab tab = threeGroups("17.33", "8.19", "41.07");
            orderDishWithoutServiceCharge(tab, "6.50");

            TabBill bill = tab.bill(TEN_PERCENT);

            assertThat(sum(bill.groups().stream().map(TabBill.SplitGroup::serviceCharge).toList()))
                    .isEqualTo(bill.serviceCharge());
            assertThat(sum(bill.groups().stream().map(TabBill.SplitGroup::total).toList()))
                    .isEqualTo(tab.total(TEN_PERCENT));
        }

        @Test
        void shouldChargeNothingToAGroupWithoutEligibleItems() {
            Tab tab = tableTab();
            orderDish(tab, "100.00");
            TabItem ineligible = orderDishWithoutServiceCharge(tab, "20.00");
            tab.assignToSplitGroup(Map.of(ineligible.id(), 2));

            TabBill.SplitGroup group = tab.bill(TEN_PERCENT).group(2);

            assertThat(group.serviceChargeBase()).isEqualTo(Money.ZERO);
            assertThat(group.serviceCharge()).isEqualTo(Money.ZERO);
            assertThat(group.total()).isEqualTo(Money.of("20.00"));
            assertThat(tab.bill(TEN_PERCENT).group(1).serviceCharge()).isEqualTo(Money.of("10.00"));
        }

        @Test
        void shouldChargeNothingToAnyGroupWhenTheTabHasNoServiceCharge() {
            Tab tab = threeGroups("10.00", "20.00", "30.00");
            tab.removeServiceCharge();

            TabBill bill = tab.bill(TEN_PERCENT);

            assertThat(bill.groups()).extracting(TabBill.SplitGroup::serviceChargeBase).containsOnly(Money.ZERO);
            assertThat(bill.groups()).extracting(TabBill.SplitGroup::serviceCharge).containsOnly(Money.ZERO);
        }

        @Test
        void shouldLeaveACancelledItemOutOfItsGroup() {
            Tab tab = tableTab();
            TabItem kept = orderDish(tab, "30.00");
            TabItem cancelled = orderDish(tab, "12.00");
            tab.cancelItem(cancelled.id(), "Cliente desistiu", WAITER, CANCELLED_AT);

            TabBill.SplitGroup group = tab.bill(TEN_PERCENT).group(1);

            assertThat(group.subtotal()).isEqualTo(Money.of("30.00"));
            assertThat(group.itemIds()).containsExactly(kept.id());
        }

        @Test
        void shouldListNoGroupHoldingOnlyCancelledItems() {
            Tab tab = tableTab();
            orderDish(tab, "30.00");
            TabItem cancelled = orderDish(tab, "12.00");
            tab.assignToSplitGroup(Map.of(cancelled.id(), 2));
            tab.cancelItem(cancelled.id(), "Cliente desistiu", WAITER, CANCELLED_AT);

            TabBill bill = tab.bill(TEN_PERCENT);

            assertThat(bill.groups()).extracting(TabBill.SplitGroup::splitGroup).containsExactly(1);
            assertRejectedWith(() -> bill.group(2), INVALID_SPLIT_GROUP);
        }

        @Test
        void shouldListTheGroupsInAscendingOrder() {
            Tab tab = tableTab();
            TabItem first = orderDish(tab, "10.00");
            TabItem second = orderDish(tab, "20.00");
            tab.assignToSplitGroup(Map.of(first.id(), 7, second.id(), 3));

            assertThat(tab.bill(TEN_PERCENT).groups()).extracting(TabBill.SplitGroup::splitGroup).containsExactly(3, 7);
        }

        private static Tab threeGroups(String first, String second, String third) {
            Tab tab = tableTab();
            orderDish(tab, first);
            TabItem two = orderDish(tab, second);
            TabItem three = orderDish(tab, third);
            tab.assignToSplitGroup(Map.of(two.id(), 2, three.id(), 3));
            return tab;
        }
    }

    @Nested
    @DisplayName("assigning items to groups")
    class Assigning {

        @ParameterizedTest(name = "group {0}")
        @CsvSource({"1", "99"})
        void shouldAcceptTheLimitsOfTheGroupNumber(int group) {
            Tab tab = tableTab();
            TabItem item = orderDish(tab, "10.00");

            tab.assignToSplitGroup(Map.of(item.id(), group));

            assertThat(tab.item(item.id()).splitGroup()).isEqualTo(group);
        }

        @ParameterizedTest(name = "group {0}")
        @CsvSource({"0", "100"})
        void shouldRefuseAGroupOutsideOneToNinetyNine(int group) {
            Tab tab = tableTab();
            TabItem item = orderDish(tab, "10.00");

            assertRejectedWith(() -> tab.assignToSplitGroup(Map.of(item.id(), group)), INVALID_SPLIT_GROUP);
        }

        @Test
        void shouldRefuseAMissingGroup() {
            Tab tab = tableTab();
            TabItem item = orderDish(tab, "10.00");
            Map<TabItemId, Integer> assignments = new HashMap<>();
            assignments.put(item.id(), null);

            assertRejectedWith(() -> tab.assignToSplitGroup(assignments), INVALID_SPLIT_GROUP);
        }

        @Test
        void shouldRefuseAnUnknownItem() {
            Tab tab = tableTab();

            assertRejectedWith(() -> tab.assignToSplitGroup(Map.of(TabItemId.newId(), 2)), TAB_ITEM_NOT_FOUND);
        }

        @Test
        void shouldCheckEveryGroupBeforeLookingForAnyItem() {
            Tab tab = tableTab();
            Map<TabItemId, Integer> assignments = new LinkedHashMap<>();
            assignments.put(TabItemId.newId(), 2);
            assignments.put(TabItemId.newId(), 100);

            assertRejectedWith(() -> tab.assignToSplitGroup(assignments), INVALID_SPLIT_GROUP);
        }

        @Test
        void shouldChangeNothingWhenOneGroupIsInvalid() {
            Tab tab = tableTab();
            TabItem first = orderDish(tab, "10.00");
            TabItem second = orderDish(tab, "20.00");
            Map<TabItemId, Integer> assignments = new LinkedHashMap<>();
            assignments.put(first.id(), 2);
            assignments.put(second.id(), 0);

            assertRejectedWith(() -> tab.assignToSplitGroup(assignments), INVALID_SPLIT_GROUP);
            assertThat(tab.item(first.id()).splitGroup()).isEqualTo(1);
        }

        @Test
        void shouldChangeNothingWhenOneItemIsUnknown() {
            Tab tab = tableTab();
            TabItem item = orderDish(tab, "10.00");
            Map<TabItemId, Integer> assignments = new LinkedHashMap<>();
            assignments.put(item.id(), 2);
            assignments.put(TabItemId.newId(), 3);

            assertRejectedWith(() -> tab.assignToSplitGroup(assignments), TAB_ITEM_NOT_FOUND);
            assertThat(tab.item(item.id()).splitGroup()).isEqualTo(1);
        }

        @Test
        void shouldMoveACancelledItem() {
            Tab tab = tableTab();
            TabItem item = orderDish(tab, "10.00");
            tab.cancelItem(item.id(), "Cliente desistiu", WAITER, CANCELLED_AT);

            tab.assignToSplitGroup(Map.of(item.id(), 4));

            assertThat(tab.item(item.id()).splitGroup()).isEqualTo(4);
        }

        @Test
        void shouldChangeNothingWithAnEmptyAssignment() {
            Tab tab = tableTab();
            TabItem item = orderDish(tab, "10.00");

            tab.assignToSplitGroup(Map.of());

            assertThat(tab.item(item.id()).splitGroup()).isEqualTo(1);
        }

        @Test
        void shouldAcceptGroupsWhileClosingWithoutTouchingTheCharge() {
            FakeTabBilling billing = new FakeTabBilling();
            Tab tab = closingTab(billing);
            TabItem item = tab.items().get(0);

            tab.assignToSplitGroup(Map.of(item.id(), 5));

            assertThat(tab.item(item.id()).splitGroup()).isEqualTo(5);
            assertThat(tab.total(TEN_PERCENT)).isEqualTo(Money.of("110.00"));
            assertThat(billing.postings).hasSize(1);
            assertThat(billing.reversals).isEmpty();
        }

        @Test
        void shouldRefuseGroupsOnAClosedTab() {
            FakeTabBilling billing = new FakeTabBilling();
            Tab tab = closedTab(billing);
            TabItem item = tab.items().get(0);

            assertRejectedWith(() -> tab.assignToSplitGroup(Map.of(item.id(), 2)), TAB_NOT_OPEN);
        }

        @Test
        void shouldCheckTheStatusBeforeTheGroupNumber() {
            Tab tab = cancelledTab();

            assertRejectedWith(() -> tab.assignToSplitGroup(Map.of(TabItemId.newId(), 0)), TAB_NOT_OPEN);
        }
    }

    @Nested
    @DisplayName("guest count")
    class GuestCount {

        @Test
        void shouldHaveNoGuestCountUntilRecorded() {
            assertThat(tableTab().guestCount()).isEmpty();
        }

        @ParameterizedTest(name = "{0} guests")
        @CsvSource({"1", "999"})
        void shouldAcceptTheLimitsOfTheGuestCount(int guests) {
            Tab tab = tableTab();

            tab.recordGuestCount(guests);

            assertThat(tab.guestCount()).contains(guests);
        }

        @ParameterizedTest(name = "{0} guests")
        @CsvSource({"0", "1000", "-1"})
        void shouldRefuseAGuestCountOutsideOneToNineHundredNinetyNine(int guests) {
            Tab tab = tableTab();

            assertRejectedWith(() -> tab.recordGuestCount(guests), INVALID_GUEST_COUNT);
            assertThat(tab.guestCount()).isEmpty();
        }

        @Test
        void shouldAcceptTheGuestCountWhileClosing() {
            Tab tab = closingTab(new FakeTabBilling());

            tab.recordGuestCount(3);

            assertThat(tab.guestCount()).contains(3);
        }

        @Test
        void shouldRefuseTheGuestCountOnAClosedTab() {
            Tab tab = closedTab(new FakeTabBilling());

            assertRejectedWith(() -> tab.recordGuestCount(2), TAB_NOT_OPEN);
        }

        @Test
        void shouldCheckTheStatusBeforeTheGuestCount() {
            Tab tab = cancelledTab();

            assertRejectedWith(() -> tab.recordGuestCount(0), TAB_NOT_OPEN);
        }
    }
}
