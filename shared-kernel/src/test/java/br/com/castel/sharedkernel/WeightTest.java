package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WeightTest {

    // ---------------------------------------------------------------------
    // priceAt(pricePerKilo)
    // ---------------------------------------------------------------------

    @Test
    void shouldPrice437GramsAt8990PerKiloAs3929() {
        Weight weight = Weight.ofGrams(437);

        Money price = weight.priceAt(Money.of("89.90"));

        assertThat(price).isEqualTo(Money.of("39.29"));
    }

    @Test
    void shouldPriceOneKiloAtExactlyThePricePerKilo() {
        Weight weight = Weight.ofGrams(1000);

        Money price = weight.priceAt(Money.of("50.00"));

        assertThat(price).isEqualTo(Money.of("50.00"));
    }

    @Test
    void shouldRoundPriceOfOneGramAt8990PerKiloToNineCents() {
        Weight weight = Weight.ofGrams(1);

        Money price = weight.priceAt(Money.of("89.90"));

        assertThat(price).isEqualTo(Money.of("0.09"));
    }

    /** 250g x 50.02/kg = 12.505: HALF_UP gives 12.51, HALF_EVEN would give 12.50. */
    @Test
    void shouldRoundPriceHalfUpWhenResultIsExactlyHalfCent() {
        Weight weight = Weight.ofGrams(250);

        Money price = weight.priceAt(Money.of("50.02"));

        assertThat(price).isEqualTo(Money.of("12.51"));
    }

    @Test
    void shouldRejectNegativePricePerKiloWithInvalidMoneyCode() {
        Weight weight = Weight.ofGrams(437);
        Money negativePricePerKilo = Money.of("89.90").negate();

        assertThatThrownBy(() -> weight.priceAt(negativePricePerKilo))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("INVALID_MONEY");
    }

    @Test
    void shouldRejectZeroPricePerKiloWithInvalidMoneyCode() {
        Weight weight = Weight.ofGrams(437);

        assertThatThrownBy(() -> weight.priceAt(Money.ZERO))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("INVALID_MONEY");
    }

    // ---------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------

    @Test
    void shouldRejectZeroGrams() {
        assertThatThrownBy(() -> Weight.ofGrams(0))
                .isInstanceOf(InvalidWeightException.class);
    }

    @Test
    void shouldRejectNegativeGrams() {
        assertThatThrownBy(() -> Weight.ofGrams(-1))
                .isInstanceOf(InvalidWeightException.class);
    }

    @Test
    void shouldRejectZeroKilos() {
        assertThatThrownBy(() -> Weight.ofKilos(BigDecimal.ZERO))
                .isInstanceOf(InvalidWeightException.class);
    }

    @Test
    void shouldRejectNegativeKilos() {
        assertThatThrownBy(() -> Weight.ofKilos(new BigDecimal("-0.437")))
                .isInstanceOf(InvalidWeightException.class);
    }

    @Test
    void shouldRejectKilosWithFractionOfGram() {
        assertThatThrownBy(() -> Weight.ofKilos(new BigDecimal("0.4375")))
                .isInstanceOf(InvalidWeightException.class);
    }

    @Test
    void shouldConsiderKilosAndEquivalentGramsEqual() {
        Weight fromKilos = Weight.ofKilos(new BigDecimal("0.437"));
        Weight fromGrams = Weight.ofGrams(437);

        assertThat(fromKilos).isEqualTo(fromGrams);
    }
}
