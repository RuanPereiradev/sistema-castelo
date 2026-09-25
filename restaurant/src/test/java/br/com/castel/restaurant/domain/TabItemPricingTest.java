package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.CANCELLED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.PROPERTY_ID;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.beer;
import static br.com.castel.restaurant.domain.TabFixtures.buffet;
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
import static br.com.castel.restaurant.domain.TabFixtures.withModifiers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.Money;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The money of a tab item, task 2.2 invariants 11 to 13 and 21. Dense on purpose: every mistake here
 * charges the guest the wrong amount.
 */
class TabItemPricingTest {

    @Nested
    @DisplayName("by unit")
    class ByUnit {

        @Test
        void shouldChargeThePriceOfTheItemTimesTheQuantity() {
            Tab tab = tableTab();

            TabItem item = order(tab, beer(), units(3));

            assertThat(item.unitPrice()).contains(Money.of("12.00"));
            assertThat(item.quantity()).isEqualTo(3);
            assertThat(item.lineTotal()).isEqualTo(Money.of("36.00"));
        }

        @Test
        void shouldDefaultTheQuantityToOneWhenTheOrderOmitsIt() {
            Tab tab = tableTab();

            TabItem item = order(tab, beer(), oneUnit());

            assertThat(item.quantity()).isEqualTo(1);
            assertThat(item.lineTotal()).isEqualTo(Money.of("12.00"));
        }

        @Test
        void shouldChargeTheLargestQuantityOf999() {
            Tab tab = tableTab();

            TabItem item = order(tab, beer(), units(999));

            assertThat(item.lineTotal()).isEqualTo(Money.of("11988.00"));
        }
    }

    @Nested
    @DisplayName("with a variant")
    class WithAVariant {

        @Test
        void shouldReplaceThePriceOfTheItemWithThePriceOfTheVariant() {
            MenuItem pizza = pizza();
            MenuItemVariant large = pizza.addVariant("G", Money.of("70.00"));
            Tab tab = tableTab();

            TabItem item = order(tab, pizza, ofVariant(large.id()));

            assertThat(item.unitPrice()).contains(Money.of("70.00"));
            assertThat(item.lineTotal()).isEqualTo(Money.of("70.00"));
            assertThat(item.variantId()).contains(large.id());
            assertThat(item.variantName()).contains("G");
        }

        @Test
        void shouldMultiplyTheVariantPriceByTheQuantity() {
            MenuItem pizza = pizza();
            MenuItemVariant small = pizza.addVariant("P", Money.of("45.00"));
            Tab tab = tableTab();

            TabItem item = order(tab, pizza, new TabItemOrder(small.id(), 2, null, List.of(), null));

            assertThat(item.lineTotal()).isEqualTo(Money.of("90.00"));
        }

        @Test
        void shouldFallBackToThePriceOfTheItemWhenEveryVariantIsDeactivated() {
            MenuItem pizza = pizza();
            MenuItemVariant small = pizza.addVariant("P", Money.of("45.00"));
            pizza.deactivateVariant(small.id());
            Tab tab = tableTab();

            TabItem item = order(tab, pizza, oneUnit());

            assertThat(item.unitPrice()).contains(Money.of("62.00"));
            assertThat(item.variantId()).isEmpty();
            assertThat(item.variantName()).isEmpty();
        }
    }

    @Nested
    @DisplayName("with modifiers")
    class WithModifiers {

        @Test
        void shouldAddEachModifierTimesItsQuantityToTheUnitPriceBeforeMultiplyingByTheQuantity() {
            MenuItem pizza = pizza();
            MenuItemVariant small = pizza.addVariant("P", Money.of("45.00"));
            Modifier crust = stuffedCrust();
            Modifier point = doneness();
            pizza.offerModifier(crust, 2);
            pizza.offerModifier(point, 1);
            Tab tab = tableTab();
            TabItemOrder request = new TabItemOrder(
                    small.id(), 3, null, List.of(new ModifierChoice(crust, 2), new ModifierChoice(point, 1)), null);

            TabItem item = order(tab, pizza, request);

            // (45.00 + 2 x 8.50 + 1 x 0.00) x 3
            assertThat(item.lineTotal()).isEqualTo(Money.of("186.00"));
        }

        @Test
        void shouldAcceptAModifierPricedAtZeroWithoutChangingTheLine() {
            MenuItem steak = steak();
            Modifier point = doneness();
            steak.offerModifier(point, 1);
            Tab tab = tableTab();

            TabItem item = order(tab, steak, withModifiers(new ModifierChoice(point, 1)));

            assertThat(item.lineTotal()).isEqualTo(Money.of("89.00"));
            assertThat(item.modifiers()).singleElement()
                    .satisfies(modifier -> assertThat(modifier.price()).isEqualTo(Money.ZERO));
        }

        @Test
        void shouldRecordEveryChosenModifierWithItsQuantity() {
            MenuItem pizza = pizza();
            Modifier crust = stuffedCrust();
            Modifier point = doneness();
            pizza.offerModifier(crust, 2);
            pizza.offerModifier(point, 1);
            Tab tab = tableTab();

            TabItem item = order(
                    tab, pizza, withModifiers(new ModifierChoice(crust, 2), new ModifierChoice(point, 1)));

            assertThat(item.modifiers())
                    .extracting(TabItemModifier::modifierId, TabItemModifier::quantity)
                    .containsExactlyInAnyOrder(
                            tuple(crust.id(), 2),
                            tuple(point.id(), 1));
        }
    }

    @Nested
    @DisplayName("by weight")
    class ByWeight {

        @Test
        void shouldChargeTheWeightAtThePricePerKiloRoundedToTheCent() {
            Tab tab = cardTab();

            TabItem item = order(tab, buffet(), grams(437));

            // 0.437 kg x 59.90 = 26.1763
            assertThat(item.lineTotal()).isEqualTo(Money.of("26.18"));
        }

        @Test
        void shouldRoundHalfACentUp() {
            MenuItem candy = MenuItem.soldByWeight(
                    PROPERTY_ID, MenuCategoryId.newId(), "Bala", PrepStation.KITCHEN, Money.of("0.20"));
            Tab tab = cardTab();

            TabItem item = order(tab, candy, grams(125));

            // 0.125 kg x 0.20 = 0.025
            assertThat(item.lineTotal()).isEqualTo(Money.of("0.03"));
        }

        @Test
        void shouldChargeTheHeaviestPlateOf50Kilos() {
            Tab tab = cardTab();

            TabItem item = order(tab, buffet(), grams(50_000));

            assertThat(item.lineTotal()).isEqualTo(Money.of("2995.00"));
        }

        @Test
        void shouldChargeTheLightestPlateOfOneGram() {
            Tab tab = cardTab();

            TabItem item = order(tab, buffet(), grams(1));

            // 0.001 kg x 59.90 = 0.0599
            assertThat(item.lineTotal()).isEqualTo(Money.of("0.06"));
        }

        @Test
        void shouldRecordTheWeightAndThePricePerKiloWithoutAUnitPrice() {
            Tab tab = cardTab();

            TabItem item = order(tab, buffet(), grams(437));

            assertThat(item.weight()).hasValueSatisfying(weight -> assertThat(weight.grams()).isEqualTo(437));
            assertThat(item.pricePerKilo()).contains(Money.of("59.90"));
            assertThat(item.unitPrice()).isEmpty();
            assertThat(item.quantity()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("frozen at ordering")
    class FrozenAtOrdering {

        @Test
        void shouldKeepTheNameAndPriceOfTheItemAfterTheMenuChanges() {
            MenuItem steak = steak();
            Tab tab = tableTab();
            TabItem item = order(tab, steak, units(2));

            steak.rename("Picanha na chapa");
            steak.changePriceTo(Money.of("99.00"));

            assertThat(item.itemName()).isEqualTo("Picanha");
            assertThat(item.unitPrice()).contains(Money.of("89.00"));
            assertThat(item.lineTotal()).isEqualTo(Money.of("178.00"));
            assertThat(tab.subtotal()).isEqualTo(Money.of("178.00"));
        }

        @Test
        void shouldKeepTheNameAndPriceOfTheVariantAfterTheMenuChanges() {
            MenuItem pizza = pizza();
            MenuItemVariant large = pizza.addVariant("G", Money.of("70.00"));
            Tab tab = tableTab();
            TabItem item = order(tab, pizza, ofVariant(large.id()));

            pizza.renameVariant(large.id(), "Grande");
            pizza.changeVariantPriceTo(large.id(), Money.of("80.00"));

            assertThat(item.variantName()).contains("G");
            assertThat(item.unitPrice()).contains(Money.of("70.00"));
            assertThat(item.lineTotal()).isEqualTo(Money.of("70.00"));
        }

        @Test
        void shouldKeepTheNameAndPriceOfEachModifierAfterTheMenuChanges() {
            MenuItem pizza = pizza();
            Modifier crust = stuffedCrust();
            pizza.offerModifier(crust, 2);
            Tab tab = tableTab();
            TabItem item = order(tab, pizza, withModifiers(new ModifierChoice(crust, 1)));

            crust.rename("Borda de chocolate");
            crust.changePriceTo(Money.of("12.00"));

            assertThat(item.modifiers()).singleElement().satisfies(modifier -> {
                assertThat(modifier.modifierName()).isEqualTo("Borda recheada");
                assertThat(modifier.price()).isEqualTo(Money.of("8.50"));
            });
            assertThat(item.lineTotal()).isEqualTo(Money.of("70.50"));
        }

        @Test
        void shouldKeepThePricePerKiloAfterTheMenuChanges() {
            MenuItem buffet = buffet();
            Tab tab = cardTab();
            TabItem item = order(tab, buffet, grams(437));

            buffet.changePriceTo(Money.of("69.90"));

            assertThat(item.pricePerKilo()).contains(Money.of("59.90"));
            assertThat(item.lineTotal()).isEqualTo(Money.of("26.18"));
        }

        @Test
        void shouldKeepThePrepStationAndTheServiceChargeAfterTheMenuChanges() {
            MenuItem steak = steak();
            Tab tab = tableTab();
            TabItem item = order(tab, steak, oneUnit());

            steak.prepareAt(PrepStation.BAR);
            steak.chargeServiceCharge(false);

            assertThat(item.prepStation()).isEqualTo(PrepStation.KITCHEN);
            assertThat(item.serviceChargeable()).isTrue();
        }
    }

    @Nested
    @DisplayName("service charge")
    class ServiceCharge {

        @Test
        void shouldChargeServiceOnAnEligibleItemAtATable() {
            Tab tab = tableTab();

            TabItem item = order(tab, steak(), oneUnit());

            assertThat(item.serviceChargeable()).isTrue();
        }

        @Test
        void shouldNotChargeServiceOnAnIneligibleItemAtATable() {
            MenuItem steak = steak();
            steak.chargeServiceCharge(false);
            Tab tab = tableTab();

            TabItem item = order(tab, steak, oneUnit());

            assertThat(item.serviceChargeable()).isFalse();
        }

        @Test
        void shouldNotChargeServiceOnAnEligibleItemOnASelfServiceCard() {
            Tab tab = cardTab();

            TabItem item = order(tab, buffet(), grams(437));

            assertThat(item.serviceChargeable()).isFalse();
        }
    }

    @Nested
    @DisplayName("subtotal")
    class Subtotal {

        @Test
        void shouldAddTheLineTotalOfEveryItem() {
            MenuItem pizza = pizza();
            MenuItemVariant large = pizza.addVariant("G", Money.of("70.00"));
            Tab tab = tableTab();
            order(tab, pizza, ofVariant(large.id()));
            order(tab, beer(), units(3));

            assertThat(tab.subtotal()).isEqualTo(Money.of("106.00"));
        }

        @Test
        void shouldAddAWeightLineToAUnitLine() {
            Tab tab = cardTab();
            order(tab, buffet(), grams(437));
            order(tab, beer(), oneUnit());

            assertThat(tab.subtotal()).isEqualTo(Money.of("38.18"));
        }

        @Test
        void shouldLeaveCancelledItemsOut() {
            Tab tab = tableTab();
            order(tab, steak(), oneUnit());
            TabItem beers = order(tab, beer(), units(3));

            tab.cancelItem(beers.id(), "Lançado na mesa errada", WAITER, CANCELLED_AT);

            assertThat(tab.subtotal()).isEqualTo(Money.of("89.00"));
        }

        @Test
        void shouldBeZeroWhenEveryItemIsCancelled() {
            Tab tab = tableTab();
            TabItem beers = order(tab, beer(), units(3));

            tab.cancelItem(beers.id(), "Cliente desistiu", WAITER, CANCELLED_AT);

            assertThat(tab.subtotal()).isEqualTo(Money.ZERO);
        }
    }
}
