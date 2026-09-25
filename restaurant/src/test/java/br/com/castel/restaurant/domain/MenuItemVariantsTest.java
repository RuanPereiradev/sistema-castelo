package br.com.castel.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.Money;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Variations (P/M/G) of a menu item, written from the task 1.2 spec (invariants 1 to 10). Dense on
 * price and availability, where a mistake charges the wrong amount or sells what the kitchen does
 * not have; lean on name validation.
 */
class MenuItemVariantsTest {

    private static final ZoneId FORTALEZA = ZoneId.of("America/Fortaleza");
    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final MenuCategoryId CATEGORY_ID = MenuCategoryId.newId();

    private static final String VARIANT_NOT_FOUND = "MENU_ITEM_VARIANT_NOT_FOUND";
    private static final String VARIANT_NAME_ALREADY_USED = "MENU_ITEM_VARIANT_NAME_ALREADY_USED";
    private static final String INVALID_VARIANT_NAME = "INVALID_MENU_ITEM_VARIANT_NAME";
    private static final String INVALID_PRICING = "INVALID_MENU_ITEM_PRICING";
    private static final String SOLD_BY_WEIGHT_REJECTS_VARIANT = "SOLD_BY_WEIGHT_REJECTS_VARIANT";

    private static MenuItem pizza() {
        return MenuItem.soldByUnit(PROPERTY_ID, CATEGORY_ID, "Pizza Calabresa", PrepStation.PIZZA, Money.of("62.00"));
    }

    private static MenuItem buffet() {
        return MenuItem.soldByWeight(PROPERTY_ID, CATEGORY_ID, "Buffet", PrepStation.KITCHEN, Money.of("89.90"));
    }

    /** A moment of a Wednesday, at the given local time in the property's zone. */
    private static Instant wednesdayAt(String localTime) {
        return LocalDateTime.of(2026, 9, 16, 0, 0).with(LocalTime.parse(localTime)).atZone(FORTALEZA).toInstant();
    }

    private static void assertRejectedWith(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(DomainException.class, failure -> assertThat(failure.code()).isEqualTo(code));
    }

    @Nested
    @DisplayName("adding a variant")
    class Adding {

        @Test
        void shouldCreateAVariantThatIsActiveAndAvailable() {
            MenuItem item = pizza();

            MenuItemVariant large = item.addVariant("G", Money.of("70.00"));

            assertThat(large.isActive()).isTrue();
            assertThat(large.isAvailable()).isTrue();
        }

        @Test
        void shouldRejectAVariantOnAnItemSoldByWeight() {
            MenuItem item = buffet();

            assertRejectedWith(() -> item.addVariant("Prato", Money.of("30.00")), SOLD_BY_WEIGHT_REJECTS_VARIANT);
        }

        @Test
        void shouldExposeTheVariantsAsAnUnmodifiableList() {
            MenuItem item = pizza();
            item.addVariant("P", Money.of("45.00"));

            assertThatThrownBy(() -> item.variants().clear()).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldRejectLookingUpAVariantThatIsNotInTheItem() {
            MenuItem item = pizza();
            item.addVariant("P", Money.of("45.00"));

            assertRejectedWith(() -> item.variant(MenuItemVariantId.newId()), VARIANT_NOT_FOUND);
        }

        @Test
        void shouldRejectLookingUpAVariantThatBelongsToAnotherItem() {
            MenuItem calabresa = pizza();
            MenuItem margherita =
                    MenuItem.soldByUnit(PROPERTY_ID, CATEGORY_ID, "Pizza Margherita", PrepStation.PIZZA, Money.of("60.00"));
            MenuItemVariant margheritaLarge = margherita.addVariant("G", Money.of("75.00"));

            assertRejectedWith(() -> calabresa.variant(margheritaLarge.id()), VARIANT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("variant name (invariants 1 and 2)")
    class Name {

        @Test
        void shouldRejectABlankName() {
            MenuItem item = pizza();

            assertRejectedWith(() -> item.addVariant("   ", Money.of("45.00")), INVALID_VARIANT_NAME);
        }

        @Test
        void shouldRejectANameLongerThanFiftyCharacters() {
            MenuItem item = pizza();

            assertRejectedWith(() -> item.addVariant("x".repeat(51), Money.of("45.00")), INVALID_VARIANT_NAME);
        }

        @Test
        void shouldAcceptANameOfExactlyFiftyCharacters() {
            MenuItem item = pizza();

            MenuItemVariant variant = item.addVariant("x".repeat(50), Money.of("45.00"));

            assertThat(item.variants()).containsExactly(variant);
        }

        @Test
        void shouldTrimTheNameAndCountTheLimitAfterTrimming() {
            MenuItem item = pizza();

            MenuItemVariant variant = item.addVariant("  " + "x".repeat(50) + "  ", Money.of("45.00"));

            assertThat(variant.name()).isEqualTo("x".repeat(50));
        }

        @Test
        void shouldAcceptRenamingAVariantToItsOwnNameInAnotherCase() {
            MenuItem item = pizza();
            MenuItemVariant large = item.addVariant("grande", Money.of("70.00"));

            item.renameVariant(large.id(), "Grande");

            assertThat(item.variant(large.id()).name()).isEqualTo("Grande");
        }

        @Test
        void shouldRejectARenameToABlankName() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));

            assertRejectedWith(() -> item.renameVariant(small.id(), " "), INVALID_VARIANT_NAME);
        }

        @Test
        void shouldRejectANameAlreadyUsedInTheSameItemIgnoringCase() {
            MenuItem item = pizza();
            item.addVariant("Grande", Money.of("70.00"));

            assertRejectedWith(() -> item.addVariant("GRANDE", Money.of("72.00")), VARIANT_NAME_ALREADY_USED);
        }

        @Test
        void shouldRejectANameAlreadyUsedByAnInactiveVariantOfTheSameItem() {
            MenuItem item = pizza();
            MenuItemVariant large = item.addVariant("Grande", Money.of("70.00"));
            item.deactivateVariant(large.id());

            assertRejectedWith(() -> item.addVariant("grande", Money.of("72.00")), VARIANT_NAME_ALREADY_USED);
        }

        @Test
        void shouldAcceptTheSameNameInAnotherItem() {
            MenuItem calabresa = pizza();
            MenuItem margherita =
                    MenuItem.soldByUnit(PROPERTY_ID, CATEGORY_ID, "Pizza Margherita", PrepStation.PIZZA, Money.of("60.00"));
            calabresa.addVariant("Grande", Money.of("70.00"));

            MenuItemVariant margheritaLarge = margherita.addVariant("Grande", Money.of("75.00"));

            assertThat(margherita.variants()).containsExactly(margheritaLarge);
        }

        @Test
        void shouldRejectARenameToTheNameOfAnotherVariantIgnoringCase() {
            MenuItem item = pizza();
            item.addVariant("Grande", Money.of("70.00"));
            MenuItemVariant medium = item.addVariant("Media", Money.of("58.00"));

            assertRejectedWith(() -> item.renameVariant(medium.id(), "grande"), VARIANT_NAME_ALREADY_USED);
        }

        @Test
        void shouldRejectRenamingAVariantThatIsNotInTheItem() {
            MenuItem item = pizza();

            assertRejectedWith(() -> item.renameVariant(MenuItemVariantId.newId(), "Grande"), VARIANT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("variant price (invariants 3, 5 and 10)")
    class Pricing {

        @Test
        void shouldRejectAVariantWithoutAPrice() {
            MenuItem item = pizza();

            assertRejectedWith(() -> item.addVariant("P", null), INVALID_PRICING);
        }

        @Test
        void shouldRejectAVariantPricedAtZero() {
            MenuItem item = pizza();

            assertRejectedWith(() -> item.addVariant("P", Money.of("0.00")), INVALID_PRICING);
        }

        @Test
        void shouldRejectAVariantWithANegativePrice() {
            MenuItem item = pizza();

            assertRejectedWith(() -> item.addVariant("P", Money.of("-45.00")), INVALID_PRICING);
        }

        @Test
        void shouldAcceptAVariantPricedAtOneCent() {
            MenuItem item = pizza();

            MenuItemVariant variant = item.addVariant("Degustacao", Money.of("0.01"));

            assertThat(variant.unitPrice()).isEqualTo(Money.of("0.01"));
        }

        @Test
        void shouldRejectChangingAVariantPriceToZero() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));

            assertRejectedWith(() -> item.changeVariantPriceTo(small.id(), Money.of("0.00")), INVALID_PRICING);
        }

        @Test
        void shouldKeepTheOldVariantPriceWhenTheNewOneIsRefused() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));

            assertThatThrownBy(() -> item.changeVariantPriceTo(small.id(), Money.of("0.00")));

            assertThat(item.variant(small.id()).unitPrice()).isEqualTo(Money.of("45.00"));
        }

        @Test
        void shouldRejectChangingThePriceOfAVariantThatIsNotInTheItem() {
            MenuItem item = pizza();

            assertRejectedWith(
                    () -> item.changeVariantPriceTo(MenuItemVariantId.newId(), Money.of("50.00")), VARIANT_NOT_FOUND);
        }

        @Test
        void shouldStartAtTheItemPriceWhenThereAreNoVariants() {
            MenuItem item = pizza();

            assertThat(item.startingPrice()).isEqualTo(Money.of("62.00"));
        }

        @Test
        void shouldStartAtTheCheapestActiveVariant() {
            MenuItem item = pizza();
            item.addVariant("M", Money.of("58.00"));
            item.addVariant("P", Money.of("45.00"));
            item.addVariant("G", Money.of("70.00"));

            assertThat(item.startingPrice()).isEqualTo(Money.of("45.00"));
        }

        @Test
        void shouldIgnoreTheItemPriceWhenItIsCheaperThanEveryVariant() {
            MenuItem item =
                    MenuItem.soldByUnit(PROPERTY_ID, CATEGORY_ID, "Pizza Portuguesa", PrepStation.PIZZA, Money.of("30.00"));
            item.addVariant("P", Money.of("45.00"));
            item.addVariant("G", Money.of("70.00"));

            assertThat(item.startingPrice()).isEqualTo(Money.of("45.00"));
        }

        @Test
        void shouldSkipAnInactiveVariantWhenFindingTheStartingPrice() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));
            item.addVariant("M", Money.of("58.00"));

            item.deactivateVariant(small.id());

            assertThat(item.startingPrice()).isEqualTo(Money.of("58.00"));
        }

        @Test
        void shouldStillStartAtASoldOutVariantBecauseItIsActive() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));
            item.addVariant("M", Money.of("58.00"));

            item.markVariantUnavailable(small.id());

            assertThat(item.startingPrice()).isEqualTo(Money.of("45.00"));
        }

        @Test
        void shouldGoBackToTheItemPriceWhenEveryVariantIsDeactivated() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));
            MenuItemVariant large = item.addVariant("G", Money.of("70.00"));

            item.deactivateVariant(small.id());
            item.deactivateVariant(large.id());

            assertThat(item.startingPrice()).isEqualTo(Money.of("62.00"));
        }

        @Test
        void shouldFollowTheVariantPriceWhenItChanges() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));
            item.addVariant("G", Money.of("70.00"));

            item.changeVariantPriceTo(small.id(), Money.of("48.50"));

            assertThat(item.startingPrice()).isEqualTo(Money.of("48.50"));
        }

        @Test
        void shouldMoveToAnotherVariantWhenTheCheapestBecomesTheMostExpensive() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));
            item.addVariant("G", Money.of("70.00"));

            item.changeVariantPriceTo(small.id(), Money.of("80.00"));

            assertThat(item.startingPrice()).isEqualTo(Money.of("70.00"));
        }

        @Test
        void shouldNotLetTheItemPriceChangeTheStartingPriceWhileVariantsAreActive() {
            MenuItem item = pizza();
            item.addVariant("P", Money.of("45.00"));

            item.changePriceTo(Money.of("10.00"));

            assertThat(item.startingPrice()).isEqualTo(Money.of("45.00"));
        }

        @Test
        void shouldUseTheUpdatedItemPriceOnceTheVariantsAreDeactivated() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));
            item.changePriceTo(Money.of("55.00"));

            item.deactivateVariant(small.id());

            assertThat(item.startingPrice()).isEqualTo(Money.of("55.00"));
        }

        @Test
        void shouldReturnToTheVariantPriceWhenAVariantIsReactivated() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));
            item.deactivateVariant(small.id());

            item.activateVariant(small.id());

            assertThat(item.startingPrice()).isEqualTo(Money.of("45.00"));
        }
    }

    @Nested
    @DisplayName("sold out and deactivated are independent (invariant 6)")
    class Independence {

        @Test
        void shouldKeepASoldOutVariantActive() {
            MenuItem item = pizza();
            MenuItemVariant large = item.addVariant("G", Money.of("70.00"));

            item.markVariantUnavailable(large.id());

            assertThat(item.variant(large.id()).isAvailable()).isFalse();
            assertThat(item.variant(large.id()).isActive()).isTrue();
        }

        @Test
        void shouldKeepADeactivatedVariantAvailable() {
            MenuItem item = pizza();
            MenuItemVariant large = item.addVariant("G", Money.of("70.00"));

            item.deactivateVariant(large.id());

            assertThat(item.variant(large.id()).isActive()).isFalse();
            assertThat(item.variant(large.id()).isAvailable()).isTrue();
        }

        @Test
        void shouldKeepAVariantSoldOutWhenItIsReactivated() {
            MenuItem item = pizza();
            MenuItemVariant large = item.addVariant("G", Money.of("70.00"));
            item.markVariantUnavailable(large.id());
            item.deactivateVariant(large.id());

            item.activateVariant(large.id());

            assertThat(item.variant(large.id()).isActive()).isTrue();
            assertThat(item.variant(large.id()).isAvailable()).isFalse();
        }

        @Test
        void shouldSellAVariantAgainWhenItIsMarkedAvailable() {
            MenuItem item = pizza();
            MenuItemVariant large = item.addVariant("G", Money.of("70.00"));
            item.markVariantUnavailable(large.id());

            item.markVariantAvailable(large.id());

            assertThat(item.variant(large.id()).isAvailable()).isTrue();
        }

        @Test
        void shouldRejectMarkingUnavailableAVariantThatIsNotInTheItem() {
            MenuItem item = pizza();

            assertRejectedWith(() -> item.markVariantUnavailable(MenuItemVariantId.newId()), VARIANT_NOT_FOUND);
        }

        @Test
        void shouldRejectDeactivatingAVariantThatIsNotInTheItem() {
            MenuItem item = pizza();

            assertRejectedWith(() -> item.deactivateVariant(MenuItemVariantId.newId()), VARIANT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("requiresVariant (invariant 7)")
    class RequiresVariant {

        @Test
        void shouldNotRequireAVariantWhenTheItemHasNone() {
            MenuItem item = pizza();

            assertThat(item.requiresVariant()).isFalse();
        }

        @Test
        void shouldRequireAVariantWhenTheItemHasAnActiveOne() {
            MenuItem item = pizza();

            item.addVariant("P", Money.of("45.00"));

            assertThat(item.requiresVariant()).isTrue();
        }

        @Test
        void shouldStillRequireAVariantWhenEveryActiveOneIsSoldOut() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));

            item.markVariantUnavailable(small.id());

            assertThat(item.requiresVariant()).isTrue();
        }

        @Test
        void shouldStillRequireAVariantWhileOneOfThemIsActive() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));
            item.addVariant("G", Money.of("70.00"));

            item.deactivateVariant(small.id());

            assertThat(item.requiresVariant()).isTrue();
        }

        @Test
        void shouldStopRequiringAVariantWhenEveryVariantIsDeactivated() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));
            MenuItemVariant large = item.addVariant("G", Money.of("70.00"));

            item.deactivateVariant(small.id());
            item.deactivateVariant(large.id());

            assertThat(item.requiresVariant()).isFalse();
        }
    }

    @Nested
    @DisplayName("availability of the item (invariant 8)")
    class ItemAvailability {

        @Test
        void shouldNotServeAnItemWhoseActiveVariantsAreAllSoldOut() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));
            MenuItemVariant large = item.addVariant("G", Money.of("70.00"));

            item.markVariantUnavailable(small.id());
            item.markVariantUnavailable(large.id());

            assertThat(item.isAvailableAt(wednesdayAt("20:00"), FORTALEZA)).isFalse();
        }

        @Test
        void shouldServeAnItemWhileOneActiveVariantIsLeft() {
            MenuItem item = pizza();
            item.addVariant("P", Money.of("45.00"));
            MenuItemVariant large = item.addVariant("G", Money.of("70.00"));

            item.markVariantUnavailable(large.id());

            assertThat(item.isAvailableAt(wednesdayAt("20:00"), FORTALEZA)).isTrue();
        }

        @Test
        void shouldNotCountAnAvailableButInactiveVariantAsSomethingToServe() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));
            MenuItemVariant large = item.addVariant("G", Money.of("70.00"));

            item.deactivateVariant(small.id());
            item.markVariantUnavailable(large.id());

            assertThat(item.isAvailableAt(wednesdayAt("20:00"), FORTALEZA)).isFalse();
        }

        @Test
        void shouldServeTheItemByItsOwnRulesWhenEveryVariantIsDeactivated() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));
            item.markVariantUnavailable(small.id());

            item.deactivateVariant(small.id());

            assertThat(item.isAvailableAt(wednesdayAt("20:00"), FORTALEZA)).isTrue();
        }

        @Test
        void shouldServeTheItemAgainWhenASoldOutVariantComesBack() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));
            item.markVariantUnavailable(small.id());

            item.markVariantAvailable(small.id());

            assertThat(item.isAvailableAt(wednesdayAt("20:00"), FORTALEZA)).isTrue();
        }

        @Test
        void shouldNotServeAnItemWithAvailableVariantsOutsideItsWindow() {
            MenuItem item = pizza();
            item.serveBetween(null, LocalTime.of(18, 30), LocalTime.of(23, 0));
            item.addVariant("P", Money.of("45.00"));

            assertThat(item.isAvailableAt(wednesdayAt("15:00"), FORTALEZA)).isFalse();
        }
    }

    @Nested
    @DisplayName("availability of a variant (invariant 9)")
    class VariantAvailability {

        @Test
        void shouldServeAnActiveAndAvailableVariantOfAnAvailableItem() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));

            assertThat(item.isVariantAvailableAt(small.id(), wednesdayAt("20:00"), FORTALEZA)).isTrue();
        }

        @Test
        void shouldNotServeASoldOutVariant() {
            MenuItem item = pizza();
            item.addVariant("P", Money.of("45.00"));
            MenuItemVariant large = item.addVariant("G", Money.of("70.00"));

            item.markVariantUnavailable(large.id());

            assertThat(item.isVariantAvailableAt(large.id(), wednesdayAt("20:00"), FORTALEZA)).isFalse();
        }

        @Test
        void shouldKeepServingTheOtherVariantsWhenOneIsSoldOut() {
            MenuItem item = pizza();
            MenuItemVariant medium = item.addVariant("M", Money.of("58.00"));
            MenuItemVariant large = item.addVariant("G", Money.of("70.00"));

            item.markVariantUnavailable(large.id());

            assertThat(item.isVariantAvailableAt(medium.id(), wednesdayAt("20:00"), FORTALEZA)).isTrue();
        }

        @Test
        void shouldNotServeAnInactiveVariant() {
            MenuItem item = pizza();
            item.addVariant("P", Money.of("45.00"));
            MenuItemVariant large = item.addVariant("G", Money.of("70.00"));

            item.deactivateVariant(large.id());

            assertThat(item.isVariantAvailableAt(large.id(), wednesdayAt("20:00"), FORTALEZA)).isFalse();
        }

        @Test
        void shouldNotServeAVariantOfAnItemTheKitchenRanOutOf() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));

            item.markUnavailable();

            assertThat(item.isVariantAvailableAt(small.id(), wednesdayAt("20:00"), FORTALEZA)).isFalse();
        }

        @Test
        void shouldNotServeAVariantOfAnItemOutOfTheMenu() {
            MenuItem item = pizza();
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));

            item.deactivate();

            assertThat(item.isVariantAvailableAt(small.id(), wednesdayAt("20:00"), FORTALEZA)).isFalse();
        }

        @Test
        void shouldNotServeAVariantOneMinuteBeforeTheItemWindowOpens() {
            MenuItem item = pizza();
            item.serveBetween(null, LocalTime.of(18, 30), LocalTime.of(23, 0));
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));

            assertThat(item.isVariantAvailableAt(small.id(), wednesdayAt("18:29"), FORTALEZA)).isFalse();
        }

        @Test
        void shouldServeAVariantOneMinuteAfterTheItemWindowOpens() {
            MenuItem item = pizza();
            item.serveBetween(null, LocalTime.of(18, 30), LocalTime.of(23, 0));
            MenuItemVariant small = item.addVariant("P", Money.of("45.00"));

            assertThat(item.isVariantAvailableAt(small.id(), wednesdayAt("18:31"), FORTALEZA)).isTrue();
        }
    }
}
