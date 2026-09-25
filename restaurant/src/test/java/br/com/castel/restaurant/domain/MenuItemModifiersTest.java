package br.com.castel.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.Money;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The link between a menu item and the modifiers it offers (invariants 14 to 20 of task 1.2). The
 * item keeps only the modifier id and the maximum quantity per order line.
 */
class MenuItemModifiersTest {

    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final MenuCategoryId CATEGORY_ID = MenuCategoryId.newId();

    private static final String INVALID_MAX_QUANTITY = "INVALID_MODIFIER_MAX_QUANTITY";
    private static final String SOLD_BY_WEIGHT_REJECTS_MODIFIER = "SOLD_BY_WEIGHT_REJECTS_MODIFIER";
    private static final String INACTIVE_MODIFIER = "INACTIVE_MODIFIER";
    private static final String MODIFIER_NOT_FOUND = "MODIFIER_NOT_FOUND";

    private static MenuItem pizza() {
        return MenuItem.soldByUnit(PROPERTY_ID, CATEGORY_ID, "Pizza Calabresa", PrepStation.PIZZA, Money.of("62.00"));
    }

    private static MenuItem buffet() {
        return MenuItem.soldByWeight(PROPERTY_ID, CATEGORY_ID, "Buffet", PrepStation.KITCHEN, Money.of("89.90"));
    }

    private static Modifier stuffedCrust() {
        return Modifier.create(PROPERTY_ID, "Borda recheada", Money.of("12.00"));
    }

    private static Modifier extraCheese() {
        return Modifier.create(PROPERTY_ID, "Queijo extra", Money.of("6.50"));
    }

    private static void assertRejectedWith(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(DomainException.class, failure -> assertThat(failure.code()).isEqualTo(code));
    }

    @Nested
    @DisplayName("offering a modifier")
    class Offering {

        @Test
        void shouldOfferAModifierWithItsMaximumQuantity() {
            MenuItem item = pizza();
            Modifier crust = stuffedCrust();

            item.offerModifier(crust, 2);

            assertThat(item.modifiers())
                    .extracting(MenuItemModifier::modifierId, MenuItemModifier::maxQuantity)
                    .containsExactly(tuple(crust.id(), 2));
        }

        @Test
        void shouldOfferAModifierThatCostsNothing() {
            MenuItem item = pizza();
            Modifier doneness = Modifier.create(PROPERTY_ID, "Ponto da carne", Money.of("0.00"));

            item.offerModifier(doneness, 1);

            assertThat(item.modifiers()).extracting(MenuItemModifier::modifierId).containsExactly(doneness.id());
        }

        @Test
        void shouldRejectAModifierOnAnItemSoldByWeight() {
            MenuItem item = buffet();

            assertRejectedWith(() -> item.offerModifier(stuffedCrust(), 1), SOLD_BY_WEIGHT_REJECTS_MODIFIER);
        }

        @Test
        void shouldRejectAnInactiveModifier() {
            MenuItem item = pizza();
            Modifier crust = stuffedCrust();
            crust.deactivate();

            assertRejectedWith(() -> item.offerModifier(crust, 1), INACTIVE_MODIFIER);
        }

        @Test
        void shouldRejectUpdatingTheLimitOfAModifierDeactivatedAfterBeingOffered() {
            MenuItem item = pizza();
            Modifier crust = stuffedCrust();
            item.offerModifier(crust, 1);
            crust.deactivate();

            assertRejectedWith(() -> item.offerModifier(crust, 2), INACTIVE_MODIFIER);
        }

        @Test
        void shouldExposeTheModifiersAsAnUnmodifiableList() {
            MenuItem item = pizza();
            item.offerModifier(stuffedCrust(), 1);

            assertThatThrownBy(() -> item.modifiers().clear()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("maximum quantity (invariant 15)")
    class MaxQuantity {

        @Test
        void shouldRejectAMaximumQuantityOfZero() {
            MenuItem item = pizza();

            assertRejectedWith(() -> item.offerModifier(stuffedCrust(), 0), INVALID_MAX_QUANTITY);
        }

        @Test
        void shouldRejectANegativeMaximumQuantity() {
            MenuItem item = pizza();

            assertRejectedWith(() -> item.offerModifier(stuffedCrust(), -1), INVALID_MAX_QUANTITY);
        }

        @Test
        void shouldAcceptAMaximumQuantityOfOne() {
            MenuItem item = pizza();

            item.offerModifier(stuffedCrust(), 1);

            assertThat(item.modifiers()).extracting(MenuItemModifier::maxQuantity).containsExactly(1);
        }

        @Test
        void shouldAcceptAMaximumQuantityOfNinetyNine() {
            MenuItem item = pizza();

            item.offerModifier(stuffedCrust(), 99);

            assertThat(item.modifiers()).extracting(MenuItemModifier::maxQuantity).containsExactly(99);
        }

        @Test
        void shouldRejectAMaximumQuantityOfOneHundred() {
            MenuItem item = pizza();

            assertRejectedWith(() -> item.offerModifier(stuffedCrust(), 100), INVALID_MAX_QUANTITY);
        }
    }

    @Nested
    @DisplayName("offering again (invariant 18)")
    class OfferingAgain {

        @Test
        void shouldUpdateTheMaximumQuantityInsteadOfDuplicatingTheModifier() {
            MenuItem item = pizza();
            Modifier crust = stuffedCrust();
            item.offerModifier(crust, 1);

            item.offerModifier(crust, 3);

            assertThat(item.modifiers())
                    .extracting(MenuItemModifier::modifierId, MenuItemModifier::maxQuantity)
                    .containsExactly(tuple(crust.id(), 3));
        }

        @Test
        void shouldKeepTheOldMaximumQuantityWhenTheNewOneIsRefused() {
            MenuItem item = pizza();
            Modifier crust = stuffedCrust();
            item.offerModifier(crust, 2);

            assertThatThrownBy(() -> item.offerModifier(crust, 100));

            assertThat(item.modifiers()).extracting(MenuItemModifier::maxQuantity).containsExactly(2);
        }

        @Test
        void shouldKeepEachModifierSeparateWhenSeveralAreOffered() {
            MenuItem item = pizza();
            Modifier crust = stuffedCrust();
            Modifier cheese = extraCheese();

            item.offerModifier(crust, 1);
            item.offerModifier(cheese, 2);

            assertThat(item.modifiers())
                    .extracting(MenuItemModifier::modifierId, MenuItemModifier::maxQuantity)
                    .containsExactlyInAnyOrder(tuple(crust.id(), 1), tuple(cheese.id(), 2));
        }
    }

    @Nested
    @DisplayName("withdrawing a modifier (invariant 19)")
    class Withdrawing {

        @Test
        void shouldUndoTheLinkWhenTheModifierIsWithdrawn() {
            MenuItem item = pizza();
            Modifier crust = stuffedCrust();
            item.offerModifier(crust, 1);

            item.withdrawModifier(crust.id());

            assertThat(item.modifiers()).isEmpty();
        }

        @Test
        void shouldKeepTheOtherModifiersWhenOneIsWithdrawn() {
            MenuItem item = pizza();
            Modifier crust = stuffedCrust();
            Modifier cheese = extraCheese();
            item.offerModifier(crust, 1);
            item.offerModifier(cheese, 2);

            item.withdrawModifier(crust.id());

            assertThat(item.modifiers()).extracting(MenuItemModifier::modifierId).containsExactly(cheese.id());
        }

        @Test
        void shouldRejectWithdrawingAModifierThatIsNotOffered() {
            MenuItem item = pizza();
            item.offerModifier(stuffedCrust(), 1);

            assertRejectedWith(() -> item.withdrawModifier(ModifierId.newId()), MODIFIER_NOT_FOUND);
        }

        @Test
        void shouldRejectWithdrawingTheSameModifierTwice() {
            MenuItem item = pizza();
            Modifier crust = stuffedCrust();
            item.offerModifier(crust, 1);
            item.withdrawModifier(crust.id());

            assertRejectedWith(() -> item.withdrawModifier(crust.id()), MODIFIER_NOT_FOUND);
        }

        @Test
        void shouldOfferAgainAModifierThatWasWithdrawn() {
            MenuItem item = pizza();
            Modifier crust = stuffedCrust();
            item.offerModifier(crust, 1);
            item.withdrawModifier(crust.id());

            item.offerModifier(crust, 4);

            assertThat(item.modifiers())
                    .extracting(MenuItemModifier::modifierId, MenuItemModifier::maxQuantity)
                    .containsExactly(tuple(crust.id(), 4));
        }
    }

    @Nested
    @DisplayName("separate aggregates (invariants 14 and 20)")
    class SeparateAggregates {

        @Test
        void shouldKeepTheLinkWhenTheModifierIsDeactivated() {
            MenuItem item = pizza();
            Modifier crust = stuffedCrust();
            item.offerModifier(crust, 2);

            crust.deactivate();

            assertThat(item.modifiers())
                    .extracting(MenuItemModifier::modifierId, MenuItemModifier::maxQuantity)
                    .containsExactly(tuple(crust.id(), 2));
        }
    }
}
