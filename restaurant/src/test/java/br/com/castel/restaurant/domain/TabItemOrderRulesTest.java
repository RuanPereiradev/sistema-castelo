package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.DUPLICATE_MODIFIER;
import static br.com.castel.restaurant.domain.TabFixtures.FORTALEZA;
import static br.com.castel.restaurant.domain.TabFixtures.INACTIVE_MODIFIER;
import static br.com.castel.restaurant.domain.TabFixtures.INVALID_MODIFIER_QUANTITY;
import static br.com.castel.restaurant.domain.TabFixtures.INVALID_QUANTITY;
import static br.com.castel.restaurant.domain.TabFixtures.INVALID_SPECIAL_INSTRUCTIONS;
import static br.com.castel.restaurant.domain.TabFixtures.INVALID_WEIGHT;
import static br.com.castel.restaurant.domain.TabFixtures.MENU_ITEM_UNAVAILABLE;
import static br.com.castel.restaurant.domain.TabFixtures.MODIFIER_NOT_OFFERED;
import static br.com.castel.restaurant.domain.TabFixtures.ORDERED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.OUTSIDE_WINDOW;
import static br.com.castel.restaurant.domain.TabFixtures.REQUIRES_WEIGHT;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_NOT_OPEN;
import static br.com.castel.restaurant.domain.TabFixtures.THURSDAY;
import static br.com.castel.restaurant.domain.TabFixtures.UNIT_REJECTS_WEIGHT;
import static br.com.castel.restaurant.domain.TabFixtures.VARIANT_NOT_FOUND;
import static br.com.castel.restaurant.domain.TabFixtures.VARIANT_REQUIRED;
import static br.com.castel.restaurant.domain.TabFixtures.VARIANT_UNAVAILABLE;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.WEIGHT_REJECTS_MODIFIER;
import static br.com.castel.restaurant.domain.TabFixtures.WEIGHT_REJECTS_QUANTITY;
import static br.com.castel.restaurant.domain.TabFixtures.WEIGHT_REJECTS_VARIANT;
import static br.com.castel.restaurant.domain.TabFixtures.assertRejectedWith;
import static br.com.castel.restaurant.domain.TabFixtures.at;
import static br.com.castel.restaurant.domain.TabFixtures.beer;
import static br.com.castel.restaurant.domain.TabFixtures.buffet;
import static br.com.castel.restaurant.domain.TabFixtures.cancelledTab;
import static br.com.castel.restaurant.domain.TabFixtures.cardTab;
import static br.com.castel.restaurant.domain.TabFixtures.doneness;
import static br.com.castel.restaurant.domain.TabFixtures.grams;
import static br.com.castel.restaurant.domain.TabFixtures.ofVariant;
import static br.com.castel.restaurant.domain.TabFixtures.oneUnit;
import static br.com.castel.restaurant.domain.TabFixtures.order;
import static br.com.castel.restaurant.domain.TabFixtures.pizza;
import static br.com.castel.restaurant.domain.TabFixtures.steak;
import static br.com.castel.restaurant.domain.TabFixtures.stuffedCrust;
import static br.com.castel.restaurant.domain.TabFixtures.tableTab;
import static br.com.castel.restaurant.domain.TabFixtures.units;
import static br.com.castel.restaurant.domain.TabFixtures.wednesdayAt;
import static br.com.castel.restaurant.domain.TabFixtures.withInstructions;
import static br.com.castel.restaurant.domain.TabFixtures.withModifiers;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.Money;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What {@code Tab.addItem} accepts and refuses, task 2.2 invariants 6 to 10, 14 and 15: one case per
 * code of section 5, then the order in which the checks run.
 */
class TabItemOrderRulesTest {

    private static TabItem orderAt(Tab tab, MenuItem menuItem, TabItemOrder request, Instant moment) {
        return tab.addItem(menuItem, request, WAITER, moment, FORTALEZA);
    }

    /** Served on Wednesdays from 11:00 to 15:00, in the zone of the property. */
    private static MenuItem lunchSpecial() {
        MenuItem item = steak();
        item.serveBetween(DayOfWeek.WEDNESDAY, LocalTime.of(11, 0), LocalTime.of(15, 0));
        return item;
    }

    @Nested
    @DisplayName("status of the tab")
    class StatusOfTheTab {

        @Test
        void shouldRejectAnItemWhenTheTabIsNotOpen() {
            Tab tab = cancelledTab();

            assertRejectedWith(() -> order(tab, beer(), oneUnit()), TAB_NOT_OPEN);
        }

        @Test
        void shouldAcceptAnItemWhileTheTabIsOpen() {
            Tab tab = tableTab();

            TabItem item = order(tab, beer(), oneUnit());

            assertThat(tab.items()).extracting(TabItem::id).containsExactly(item.id());
        }
    }

    @Nested
    @DisplayName("availability of the menu item")
    class Availability {

        @Test
        void shouldRejectAnInactiveItem() {
            MenuItem beer = beer();
            beer.deactivate();

            assertRejectedWith(() -> order(tableTab(), beer, oneUnit()), MENU_ITEM_UNAVAILABLE);
        }

        @Test
        void shouldRejectAnItemThatRanOut() {
            MenuItem beer = beer();
            beer.markUnavailable();

            assertRejectedWith(() -> order(tableTab(), beer, oneUnit()), MENU_ITEM_UNAVAILABLE);
        }

        @Test
        void shouldRejectAnItemWhoseActiveVariantsAllRanOut() {
            MenuItem pizza = pizza();
            MenuItemVariant small = pizza.addVariant("P", Money.of("45.00"));
            MenuItemVariant large = pizza.addVariant("G", Money.of("70.00"));
            pizza.markVariantUnavailable(small.id());
            pizza.markVariantUnavailable(large.id());

            assertRejectedWith(() -> order(tableTab(), pizza, ofVariant(small.id())), MENU_ITEM_UNAVAILABLE);
        }

        @Test
        void shouldRejectAnItemBeforeItsWindowOpens() {
            Tab tab = tableTab();

            assertRejectedWith(
                    () -> orderAt(tab, lunchSpecial(), oneUnit(), wednesdayAt("10:59")), OUTSIDE_WINDOW);
        }

        @Test
        void shouldRejectAnItemOnADayItIsNotServed() {
            Tab tab = tableTab();

            assertRejectedWith(() -> orderAt(tab, lunchSpecial(), oneUnit(), at(THURSDAY, "12:00")), OUTSIDE_WINDOW);
        }

        @Test
        void shouldAcceptAnItemAsItsWindowOpens() {
            Tab tab = tableTab();

            TabItem item = orderAt(tab, lunchSpecial(), oneUnit(), wednesdayAt("11:00"));

            assertThat(tab.items()).extracting(TabItem::id).containsExactly(item.id());
        }

        @Test
        void shouldReadTheWindowOnTheClockOfTheProperty() {
            Tab tab = tableTab();
            // 14:30 in Fortaleza is 17:30 UTC, outside the window if read in UTC
            Instant halfPastTwoInFortaleza = Instant.parse("2026-09-16T17:30:00Z");

            TabItem item = orderAt(tab, lunchSpecial(), oneUnit(), halfPastTwoInFortaleza);

            assertThat(tab.items()).extracting(TabItem::id).containsExactly(item.id());
        }
    }

    @Nested
    @DisplayName("sold by weight")
    class SoldByWeight {

        @Test
        void shouldRequireAWeight() {
            assertRejectedWith(() -> order(cardTab(), buffet(), grams(null)), REQUIRES_WEIGHT);
        }

        @Test
        void shouldRejectZeroGrams() {
            assertRejectedWith(() -> order(cardTab(), buffet(), grams(0)), INVALID_WEIGHT);
        }

        @Test
        void shouldRejectOneGramAbove50Kilos() {
            assertRejectedWith(() -> order(cardTab(), buffet(), grams(50_001)), INVALID_WEIGHT);
        }

        @Test
        void shouldRejectAQuantityOtherThanOne() {
            TabItemOrder request = new TabItemOrder(null, 2, 437, List.of(), null);

            assertRejectedWith(() -> order(cardTab(), buffet(), request), WEIGHT_REJECTS_QUANTITY);
        }

        @Test
        void shouldAcceptAnExplicitQuantityOfOne() {
            Tab tab = cardTab();

            TabItem item = order(tab, buffet(), new TabItemOrder(null, 1, 437, List.of(), null));

            assertThat(item.quantity()).isEqualTo(1);
        }

        @Test
        void shouldRejectAVariant() {
            TabItemOrder request = new TabItemOrder(MenuItemVariantId.newId(), null, 437, List.of(), null);

            assertRejectedWith(() -> order(cardTab(), buffet(), request), WEIGHT_REJECTS_VARIANT);
        }

        @Test
        void shouldRejectAModifier() {
            TabItemOrder request =
                    new TabItemOrder(null, null, 437, List.of(new ModifierChoice(doneness(), 1)), null);

            assertRejectedWith(() -> order(cardTab(), buffet(), request), WEIGHT_REJECTS_MODIFIER);
        }
    }

    @Nested
    @DisplayName("sold by unit")
    class SoldByUnit {

        @Test
        void shouldRejectAWeight() {
            assertRejectedWith(() -> order(tableTab(), beer(), grams(350)), UNIT_REJECTS_WEIGHT);
        }

        @Test
        void shouldRejectAQuantityOfZero() {
            assertRejectedWith(() -> order(tableTab(), beer(), units(0)), INVALID_QUANTITY);
        }

        @Test
        void shouldRejectAQuantityOf1000() {
            assertRejectedWith(() -> order(tableTab(), beer(), units(1000)), INVALID_QUANTITY);
        }
    }

    @Nested
    @DisplayName("variant")
    class Variant {

        @Test
        void shouldRequireAVariantWhenTheItemHasActiveVariants() {
            MenuItem pizza = pizza();
            pizza.addVariant("P", Money.of("45.00"));

            assertRejectedWith(() -> order(tableTab(), pizza, oneUnit()), VARIANT_REQUIRED);
        }

        @Test
        void shouldRejectAVariantOfAnotherItem() {
            MenuItem pizza = pizza();
            pizza.addVariant("P", Money.of("45.00"));
            MenuItemVariant otherPizzasSize = pizza().addVariant("P", Money.of("45.00"));

            assertRejectedWith(() -> order(tableTab(), pizza, ofVariant(otherPizzasSize.id())), VARIANT_NOT_FOUND);
        }

        @Test
        void shouldRejectAVariantThatRanOut() {
            MenuItem pizza = pizza();
            MenuItemVariant small = pizza.addVariant("P", Money.of("45.00"));
            pizza.addVariant("G", Money.of("70.00"));
            pizza.markVariantUnavailable(small.id());

            assertRejectedWith(() -> order(tableTab(), pizza, ofVariant(small.id())), VARIANT_UNAVAILABLE);
        }

        @Test
        void shouldRejectAnInactiveVariant() {
            MenuItem pizza = pizza();
            MenuItemVariant small = pizza.addVariant("P", Money.of("45.00"));
            pizza.addVariant("G", Money.of("70.00"));
            pizza.deactivateVariant(small.id());

            assertRejectedWith(() -> order(tableTab(), pizza, ofVariant(small.id())), VARIANT_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("modifiers")
    class Modifiers {

        @Test
        void shouldRejectAModifierTheItemDoesNotOffer() {
            assertRejectedWith(
                    () -> order(tableTab(), pizza(), withModifiers(new ModifierChoice(stuffedCrust(), 1))),
                    MODIFIER_NOT_OFFERED);
        }

        @Test
        void shouldRejectAModifierDeactivatedAfterBeingOffered() {
            MenuItem pizza = pizza();
            Modifier crust = stuffedCrust();
            pizza.offerModifier(crust, 2);
            crust.deactivate();

            assertRejectedWith(
                    () -> order(tableTab(), pizza, withModifiers(new ModifierChoice(crust, 1))), INACTIVE_MODIFIER);
        }

        @Test
        void shouldRejectAModifierQuantityOfZero() {
            MenuItem pizza = pizza();
            Modifier crust = stuffedCrust();
            pizza.offerModifier(crust, 2);

            assertRejectedWith(
                    () -> order(tableTab(), pizza, withModifiers(new ModifierChoice(crust, 0))),
                    INVALID_MODIFIER_QUANTITY);
        }

        @Test
        void shouldRejectOneModifierAboveTheMaximumTheItemAllows() {
            MenuItem pizza = pizza();
            Modifier crust = stuffedCrust();
            pizza.offerModifier(crust, 2);

            assertRejectedWith(
                    () -> order(tableTab(), pizza, withModifiers(new ModifierChoice(crust, 3))),
                    INVALID_MODIFIER_QUANTITY);
        }

        @Test
        void shouldCountTheModifierMaximumPerUnitNotPerLine() {
            MenuItem pizza = pizza();
            Modifier crust = stuffedCrust();
            pizza.offerModifier(crust, 2);
            Tab tab = tableTab();

            TabItem item = order(tab, pizza, new TabItemOrder(null, 3, null, List.of(new ModifierChoice(crust, 2)), null));

            assertThat(item.modifiers()).singleElement()
                    .satisfies(modifier -> assertThat(modifier.quantity()).isEqualTo(2));
        }

        @Test
        void shouldRejectTheSameModifierTwice() {
            MenuItem pizza = pizza();
            Modifier crust = stuffedCrust();
            pizza.offerModifier(crust, 2);

            assertRejectedWith(
                    () -> order(
                            tableTab(),
                            pizza,
                            withModifiers(new ModifierChoice(crust, 1), new ModifierChoice(crust, 1))),
                    DUPLICATE_MODIFIER);
        }
    }

    @Nested
    @DisplayName("special instructions")
    class SpecialInstructions {

        @Test
        void shouldTrimTheInstructions() {
            Tab tab = tableTab();

            TabItem item = order(tab, steak(), withInstructions("  sem cebola  "));

            assertThat(item.specialInstructions()).contains("sem cebola");
        }

        @Test
        void shouldTurnBlankInstructionsIntoNone() {
            Tab tab = tableTab();

            TabItem item = order(tab, steak(), withInstructions("   "));

            assertThat(item.specialInstructions()).isEmpty();
        }

        @Test
        void shouldAcceptInstructionsOf200Characters() {
            Tab tab = tableTab();

            TabItem item = order(tab, steak(), withInstructions("x".repeat(200)));

            assertThat(item.specialInstructions()).contains("x".repeat(200));
        }

        @Test
        void shouldRejectInstructionsOf201Characters() {
            assertRejectedWith(
                    () -> order(tableTab(), steak(), withInstructions("x".repeat(201))), INVALID_SPECIAL_INSTRUCTIONS);
        }
    }

    @Nested
    @DisplayName("status at birth")
    class StatusAtBirth {

        @Test
        void shouldBeBornPendingWhenSoldByUnit() {
            Tab tab = tableTab();

            TabItem item = order(tab, steak(), oneUnit());

            assertThat(item.status()).isEqualTo(TabItemStatus.PENDING);
        }

        @Test
        void shouldBeBornDeliveredWhenSoldByWeight() {
            Tab tab = cardTab();

            TabItem item = order(tab, buffet(), grams(437));

            assertThat(item.status()).isEqualTo(TabItemStatus.DELIVERED);
        }

        @Test
        void shouldSendADrinkOrderedOnACardToTheBarAsPending() {
            Tab tab = cardTab();

            TabItem item = order(tab, beer(), oneUnit());

            assertThat(item.status()).isEqualTo(TabItemStatus.PENDING);
            assertThat(item.prepStation()).isEqualTo(PrepStation.BAR);
        }

        @Test
        void shouldBeBornActiveAndRecordWhoOrderedItAndWhen() {
            Tab tab = tableTab();

            TabItem item = order(tab, steak(), oneUnit());

            assertThat(item.isActive()).isTrue();
            assertThat(item.orderedBy()).isEqualTo(WAITER);
            assertThat(item.orderedAt()).isEqualTo(ORDERED_AT);
            assertThat(item.cancelledAt()).isEmpty();
        }
    }

    @Nested
    @DisplayName("order of the checks, section 5")
    class OrderOfTheChecks {

        @Test
        void shouldCheckTheTabBeforeTheItem() {
            MenuItem beer = beer();
            beer.markUnavailable();

            assertRejectedWith(() -> order(cancelledTab(), beer, oneUnit()), TAB_NOT_OPEN);
        }

        @Test
        void shouldCheckThatTheItemRanOutBeforeItsWindow() {
            MenuItem special = lunchSpecial();
            special.markUnavailable();

            assertRejectedWith(() -> orderAt(tableTab(), special, oneUnit(), at(THURSDAY, "12:00")), MENU_ITEM_UNAVAILABLE);
        }

        @Test
        void shouldCheckTheWindowBeforeHowTheItemIsSold() {
            assertRejectedWith(
                    () -> orderAt(tableTab(), lunchSpecial(), grams(350), at(THURSDAY, "12:00")), OUTSIDE_WINDOW);
        }

        @Test
        void shouldCheckHowTheItemIsSoldBeforeTheVariant() {
            MenuItem pizza = pizza();
            pizza.addVariant("P", Money.of("45.00"));

            assertRejectedWith(() -> order(tableTab(), pizza, grams(350)), UNIT_REJECTS_WEIGHT);
        }

        @Test
        void shouldCheckTheVariantOfAWeighedItemBeforeItsQuantity() {
            TabItemOrder request = new TabItemOrder(MenuItemVariantId.newId(), 2, 437, List.of(), null);

            assertRejectedWith(() -> order(cardTab(), buffet(), request), WEIGHT_REJECTS_VARIANT);
        }

        @Test
        void shouldCheckTheQuantityOfAWeighedItemBeforeItsWeight() {
            TabItemOrder request = new TabItemOrder(null, 2, null, List.of(), null);

            assertRejectedWith(() -> order(cardTab(), buffet(), request), WEIGHT_REJECTS_QUANTITY);
        }

        @Test
        void shouldCheckTheVariantBeforeTheModifiers() {
            MenuItem pizza = pizza();
            MenuItemVariant small = pizza.addVariant("P", Money.of("45.00"));
            pizza.addVariant("G", Money.of("70.00"));
            pizza.markVariantUnavailable(small.id());
            TabItemOrder request =
                    new TabItemOrder(small.id(), null, null, List.of(new ModifierChoice(stuffedCrust(), 1)), null);

            assertRejectedWith(() -> order(tableTab(), pizza, request), VARIANT_UNAVAILABLE);
        }

        @Test
        void shouldCheckForARepeatedModifierBeforeWhetherItIsOffered() {
            Modifier crust = stuffedCrust();

            assertRejectedWith(
                    () -> order(
                            tableTab(),
                            pizza(),
                            withModifiers(new ModifierChoice(crust, 1), new ModifierChoice(crust, 1))),
                    DUPLICATE_MODIFIER);
        }

        @Test
        void shouldCheckWhetherTheModifierIsOfferedBeforeWhetherItIsActive() {
            Modifier crust = stuffedCrust();
            crust.deactivate();

            assertRejectedWith(
                    () -> order(tableTab(), pizza(), withModifiers(new ModifierChoice(crust, 1))),
                    MODIFIER_NOT_OFFERED);
        }

        @Test
        void shouldCheckWhetherTheModifierIsActiveBeforeItsQuantity() {
            MenuItem pizza = pizza();
            Modifier crust = stuffedCrust();
            pizza.offerModifier(crust, 2);
            crust.deactivate();

            assertRejectedWith(
                    () -> order(tableTab(), pizza, withModifiers(new ModifierChoice(crust, 0))), INACTIVE_MODIFIER);
        }

        @Test
        void shouldCheckTheModifiersBeforeTheInstructions() {
            MenuItem pizza = pizza();
            Modifier crust = stuffedCrust();
            pizza.offerModifier(crust, 2);
            TabItemOrder request =
                    new TabItemOrder(null, null, null, List.of(new ModifierChoice(crust, 3)), "x".repeat(201));

            assertRejectedWith(() -> order(tableTab(), pizza, request), INVALID_MODIFIER_QUANTITY);
        }

        @Test
        void shouldLeaveTheTabUntouchedWhenAnOrderIsRejected() {
            Tab tab = tableTab();
            order(tab, beer(), oneUnit());

            assertRejectedWith(() -> order(tab, steak(), withInstructions("x".repeat(201))), INVALID_SPECIAL_INSTRUCTIONS);

            assertThat(tab.items()).hasSize(1);
            assertThat(tab.subtotal()).isEqualTo(Money.of("12.00"));
        }
    }
}
