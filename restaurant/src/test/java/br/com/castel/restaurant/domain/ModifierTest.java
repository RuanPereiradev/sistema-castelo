package br.com.castel.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.Money;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The property's modifier catalogue (invariants 11, 13 and 14 of task 1.2). Uniqueness of the name
 * (invariant 12) is checked by the use case and is not covered here.
 */
class ModifierTest {

    private static final UUID PROPERTY_ID = UUID.randomUUID();

    private static final String INVALID_MODIFIER_NAME = "INVALID_MODIFIER_NAME";
    private static final String INVALID_MODIFIER_PRICE = "INVALID_MODIFIER_PRICE";

    private static Modifier stuffedCrust() {
        return Modifier.create(PROPERTY_ID, "Borda recheada", Money.of("12.00"));
    }

    private static void assertRejectedWith(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(DomainException.class, failure -> assertThat(failure.code()).isEqualTo(code));
    }

    @Nested
    @DisplayName("price (invariant 13)")
    class Pricing {

        @Test
        void shouldAcceptAModifierThatCostsNothing() {
            Modifier doneness = Modifier.create(PROPERTY_ID, "Ponto da carne", Money.of("0.00"));

            assertThat(doneness.price()).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldAcceptAModifierPricedAtOneCent() {
            Modifier sauce = Modifier.create(PROPERTY_ID, "Sache de molho", Money.of("0.01"));

            assertThat(sauce.price()).isEqualTo(Money.of("0.01"));
        }

        @Test
        void shouldRejectANegativePrice() {
            assertRejectedWith(
                    () -> Modifier.create(PROPERTY_ID, "Sem cebola", Money.of("-0.01")), INVALID_MODIFIER_PRICE);
        }

        @Test
        void shouldRejectAMissingPrice() {
            assertRejectedWith(() -> Modifier.create(PROPERTY_ID, "Borda recheada", null), INVALID_MODIFIER_PRICE);
        }

        @Test
        void shouldAcceptChangingThePriceToZero() {
            Modifier crust = stuffedCrust();

            crust.changePriceTo(Money.of("0.00"));

            assertThat(crust.price()).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldRejectChangingThePriceToANegativeAmount() {
            Modifier crust = stuffedCrust();

            assertRejectedWith(() -> crust.changePriceTo(Money.of("-1.00")), INVALID_MODIFIER_PRICE);
        }

        @Test
        void shouldKeepTheOldPriceWhenTheNewOneIsRefused() {
            Modifier crust = stuffedCrust();

            assertThatThrownBy(() -> crust.changePriceTo(Money.of("-1.00")));

            assertThat(crust.price()).isEqualTo(Money.of("12.00"));
        }
    }

    @Nested
    @DisplayName("name (invariant 11)")
    class Name {

        @Test
        void shouldRejectABlankName() {
            assertRejectedWith(() -> Modifier.create(PROPERTY_ID, "   ", Money.of("12.00")), INVALID_MODIFIER_NAME);
        }

        @Test
        void shouldRejectANameLongerThanOneHundredCharacters() {
            assertRejectedWith(
                    () -> Modifier.create(PROPERTY_ID, "x".repeat(101), Money.of("12.00")), INVALID_MODIFIER_NAME);
        }

        @Test
        void shouldAcceptANameOfExactlyOneHundredCharacters() {
            Modifier modifier = Modifier.create(PROPERTY_ID, "x".repeat(100), Money.of("12.00"));

            assertThat(modifier.name()).hasSize(100);
        }

        @Test
        void shouldRejectARenameToABlankName() {
            Modifier crust = stuffedCrust();

            assertRejectedWith(() -> crust.rename(""), INVALID_MODIFIER_NAME);
        }
    }

    @Nested
    @DisplayName("activation (invariant 14)")
    class Activation {

        @Test
        void shouldBeBornActive() {
            Modifier crust = stuffedCrust();

            assertThat(crust.isActive()).isTrue();
        }

        @Test
        void shouldStopBeingActiveWhenDeactivated() {
            Modifier crust = stuffedCrust();

            crust.deactivate();

            assertThat(crust.isActive()).isFalse();
        }

        @Test
        void shouldBeActiveAgainWhenReactivated() {
            Modifier crust = stuffedCrust();
            crust.deactivate();

            crust.activate();

            assertThat(crust.isActive()).isTrue();
        }
    }
}
