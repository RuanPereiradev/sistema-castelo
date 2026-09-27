package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.CANCELLED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_ITEM_NOT_FOUND;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_ITEM_NOT_SERVICE_CHARGEABLE;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_NOT_OPEN;
import static br.com.castel.restaurant.domain.TabFixtures.TEN_PERCENT;
import static br.com.castel.restaurant.domain.TabFixtures.TWENTY_PERCENT;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.assertRejectedWith;
import static br.com.castel.restaurant.domain.TabFixtures.buffet;
import static br.com.castel.restaurant.domain.TabFixtures.cancelledTab;
import static br.com.castel.restaurant.domain.TabFixtures.cardTab;
import static br.com.castel.restaurant.domain.TabFixtures.closingTab;
import static br.com.castel.restaurant.domain.TabFixtures.grams;
import static br.com.castel.restaurant.domain.TabFixtures.order;
import static br.com.castel.restaurant.domain.TabFixtures.orderDish;
import static br.com.castel.restaurant.domain.TabFixtures.orderDishWithoutServiceCharge;
import static br.com.castel.restaurant.domain.TabFixtures.pizza;
import static br.com.castel.restaurant.domain.TabFixtures.stuffedCrust;
import static br.com.castel.restaurant.domain.TabFixtures.tableTab;
import static br.com.castel.restaurant.domain.TabFixtures.withModifiers;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.sharedkernel.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Service charge of a tab, task 3.2 invariants 1 to 6. */
class TabServiceChargeTest {

    @Nested
    @DisplayName("amount")
    class Amount {

        @ParameterizedTest(name = "base {0} -> {1}")
        @CsvSource({"100.00, 10.00", "33.35, 3.34", "0.05, 0.01", "0.04, 0.00"})
        void shouldChargeTheRateOverTheBaseRoundedHalfUpToTheCent(String base, String expected) {
            Tab tab = tableTab();
            orderDish(tab, base);

            assertThat(tab.serviceCharge(TEN_PERCENT)).isEqualTo(Money.of(expected));
        }

        @Test
        void shouldChargeAPlateSoldByWeightOnATableTab() {
            Tab tab = tableTab();
            order(tab, buffet(), grams(453));

            // 453 g x 59.90/kg = 27.1347 -> 27.13; 10% = 2.713 -> 2.71
            assertThat(tab.serviceChargeBase()).isEqualTo(Money.of("27.13"));
            assertThat(tab.serviceCharge(TEN_PERCENT)).isEqualTo(Money.of("2.71"));
        }

        @Test
        void shouldChargeOnceOverTheSumAndNeverItemByItem() {
            Tab tab = tableTab();
            orderDish(tab, "3.35");
            orderDish(tab, "3.35");
            orderDish(tab, "3.35");

            // 10.05 x 10% = 1.005 -> 1.01; item by item would be 3 x 0.34 = 1.02
            assertThat(tab.serviceCharge(TEN_PERCENT)).isEqualTo(Money.of("1.01"));
        }

        @Test
        void shouldAddTheServiceChargeToTheSubtotalForTheTotal() {
            Tab tab = tableTab();
            orderDish(tab, "33.35");
            orderDishWithoutServiceCharge(tab, "5.00");

            assertThat(tab.total(TEN_PERCENT)).isEqualTo(Money.of("41.69"));
        }

        @Test
        void shouldLeaveACancelledItemOutOfTheBase() {
            Tab tab = tableTab();
            orderDish(tab, "100.00");
            TabItem cancelled = orderDish(tab, "50.00");
            tab.cancelItem(cancelled.id(), "Cliente desistiu", WAITER, CANCELLED_AT);

            assertThat(tab.serviceChargeBase()).isEqualTo(Money.of("100.00"));
        }

        @Test
        void shouldChargeTheModifierAlongWithItsItem() {
            Tab tab = tableTab();
            MenuItem pizza = pizza();
            Modifier crust = stuffedCrust();
            pizza.offerModifier(crust, 1);
            order(tab, pizza, withModifiers(new ModifierChoice(crust, 1)));

            // 62.00 + 8.50
            assertThat(tab.serviceChargeBase()).isEqualTo(Money.of("70.50"));
            assertThat(tab.serviceCharge(TEN_PERCENT)).isEqualTo(Money.of("7.05"));
        }

        @Test
        void shouldLeaveAnIneligibleItemOutOfTheBaseButInTheSubtotal() {
            Tab tab = tableTab();
            orderDish(tab, "100.00");
            orderDishWithoutServiceCharge(tab, "20.00");

            assertThat(tab.serviceChargeBase()).isEqualTo(Money.of("100.00"));
            assertThat(tab.subtotal()).isEqualTo(Money.of("120.00"));
        }

        @Test
        void shouldNotApplyServiceChargeOnSelfServiceTab() {
            Tab tab = cardTab();
            order(tab, buffet(), grams(500));

            assertThat(tab.serviceChargeApplied()).isFalse();
            assertThat(tab.serviceChargeBase()).isEqualTo(Money.ZERO);
            assertThat(tab.total(TEN_PERCENT)).isEqualTo(tab.subtotal());
        }

        @Test
        void shouldUseTheFrozenRateOnceClosingStartedIgnoringTheCurrentOne() {
            Tab tab = closingTab(new FakeTabBilling());

            assertThat(tab.serviceChargeRate()).contains(TEN_PERCENT);
            assertThat(tab.serviceCharge(TWENTY_PERCENT)).isEqualTo(Money.of("10.00"));
            assertThat(tab.bill(TWENTY_PERCENT).serviceChargeRate()).isEqualTo(TEN_PERCENT);
        }

        @Test
        void shouldUseTheCurrentRateWhileOpen() {
            Tab tab = tableTab();
            orderDish(tab, "100.00");

            assertThat(tab.serviceChargeRate()).isEmpty();
            assertThat(tab.serviceCharge(TWENTY_PERCENT)).isEqualTo(Money.of("20.00"));
        }
    }

    @Nested
    @DisplayName("on the whole tab")
    class OnTheWholeTab {

        @Test
        void shouldChargeNothingOnceRemovedFromTheTab() {
            Tab tab = tableTab();
            orderDish(tab, "100.00");

            tab.removeServiceCharge();

            assertThat(tab.serviceChargeApplied()).isFalse();
            assertThat(tab.serviceChargeBase()).isEqualTo(Money.ZERO);
            assertThat(tab.total(TEN_PERCENT)).isEqualTo(Money.of("100.00"));
        }

        @Test
        void shouldChargeAgainOnceRestoredOnATableTab() {
            Tab tab = tableTab();
            orderDish(tab, "100.00");
            tab.removeServiceCharge();

            tab.restoreServiceCharge();

            assertThat(tab.serviceChargeApplied()).isTrue();
            assertThat(tab.serviceCharge(TEN_PERCENT)).isEqualTo(Money.of("10.00"));
        }

        @Test
        void shouldKeepTheServiceChargeOffWhenRestoredOnASelfServiceTab() {
            Tab tab = cardTab();
            order(tab, buffet(), grams(500));

            tab.restoreServiceCharge();

            assertThat(tab.serviceChargeApplied()).isFalse();
            assertThat(tab.serviceChargeBase()).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldAcceptRemovingTwiceInSilence() {
            Tab tab = tableTab();
            tab.removeServiceCharge();

            tab.removeServiceCharge();

            assertThat(tab.serviceChargeApplied()).isFalse();
        }

        @Test
        void shouldRefuseRemovingTheServiceChargeWhenClosing() {
            Tab tab = closingTab(new FakeTabBilling());

            assertRejectedWith(tab::removeServiceCharge, TAB_NOT_OPEN);
            assertThat(tab.serviceChargeApplied()).isTrue();
        }

        @Test
        void shouldRefuseRestoringTheServiceChargeWhenCancelled() {
            Tab tab = cancelledTab();

            assertRejectedWith(tab::restoreServiceCharge, TAB_NOT_OPEN);
        }
    }

    @Nested
    @DisplayName("on one item")
    class OnOneItem {

        @Test
        void shouldLeaveAWaivedItemOutOfTheBase() {
            Tab tab = tableTab();
            orderDish(tab, "100.00");
            TabItem waived = orderDish(tab, "40.00");

            tab.removeServiceChargeFrom(waived.id());

            assertThat(tab.item(waived.id()).serviceChargeWaived()).isTrue();
            assertThat(tab.item(waived.id()).countsForServiceCharge()).isFalse();
            assertThat(tab.serviceChargeBase()).isEqualTo(Money.of("100.00"));
        }

        @Test
        void shouldCountTheItemAgainOnceRestored() {
            Tab tab = tableTab();
            orderDish(tab, "100.00");
            TabItem item = orderDish(tab, "40.00");
            tab.removeServiceChargeFrom(item.id());

            tab.restoreServiceChargeTo(item.id());

            assertThat(tab.item(item.id()).countsForServiceCharge()).isTrue();
            assertThat(tab.serviceChargeBase()).isEqualTo(Money.of("140.00"));
        }

        @Test
        void shouldNotCountACancelledItem() {
            Tab tab = tableTab();
            TabItem item = orderDish(tab, "40.00");
            tab.cancelItem(item.id(), "Cliente desistiu", WAITER, CANCELLED_AT);

            assertThat(tab.item(item.id()).countsForServiceCharge()).isFalse();
        }

        @Test
        void shouldCheckTheStatusBeforeLookingForTheItem() {
            Tab tab = closingTab(new FakeTabBilling());

            assertRejectedWith(() -> tab.removeServiceChargeFrom(TabItemId.newId()), TAB_NOT_OPEN);
        }

        @Test
        void shouldRefuseAnUnknownItem() {
            Tab tab = tableTab();

            assertRejectedWith(() -> tab.restoreServiceChargeTo(TabItemId.newId()), TAB_ITEM_NOT_FOUND);
        }

        @Test
        void shouldRefuseRemovingFromAnItemThatNeverHadTheServiceCharge() {
            Tab tab = tableTab();
            TabItem item = orderDishWithoutServiceCharge(tab, "20.00");

            assertRejectedWith(() -> tab.removeServiceChargeFrom(item.id()), TAB_ITEM_NOT_SERVICE_CHARGEABLE);
        }

        @Test
        void shouldRefuseRestoringToAnItemThatNeverHadTheServiceCharge() {
            Tab tab = tableTab();
            TabItem item = orderDishWithoutServiceCharge(tab, "20.00");

            assertRejectedWith(() -> tab.restoreServiceChargeTo(item.id()), TAB_ITEM_NOT_SERVICE_CHARGEABLE);
            assertThat(tab.serviceChargeBase()).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldRefuseRestoringToASelfServiceItem() {
            Tab tab = cardTab();
            TabItem plate = order(tab, buffet(), grams(500));

            assertRejectedWith(() -> tab.restoreServiceChargeTo(plate.id()), TAB_ITEM_NOT_SERVICE_CHARGEABLE);
        }

        @Test
        void shouldCheckEligibilityBeforeAcceptingACancelledItemInSilence() {
            Tab tab = tableTab();
            TabItem item = orderDishWithoutServiceCharge(tab, "20.00");
            tab.cancelItem(item.id(), "Cliente desistiu", WAITER, CANCELLED_AT);

            assertRejectedWith(() -> tab.removeServiceChargeFrom(item.id()), TAB_ITEM_NOT_SERVICE_CHARGEABLE);
        }

        @Test
        void shouldAcceptACancelledItemInSilenceWithoutChangingIt() {
            Tab tab = tableTab();
            TabItem item = orderDish(tab, "40.00");
            tab.cancelItem(item.id(), "Cliente desistiu", WAITER, CANCELLED_AT);

            tab.removeServiceChargeFrom(item.id());

            assertThat(tab.item(item.id()).serviceChargeWaived()).isFalse();
        }

        @Test
        void shouldAcceptWaivingTwiceInSilence() {
            Tab tab = tableTab();
            TabItem item = orderDish(tab, "40.00");
            tab.removeServiceChargeFrom(item.id());

            tab.removeServiceChargeFrom(item.id());

            assertThat(tab.item(item.id()).serviceChargeWaived()).isTrue();
        }

        @Test
        void shouldAcceptRestoringAnItemThatWasNeverWaivedInSilence() {
            Tab tab = tableTab();
            TabItem item = orderDish(tab, "40.00");

            tab.restoreServiceChargeTo(item.id());

            assertThat(tab.item(item.id()).serviceChargeWaived()).isFalse();
        }
    }
}
