package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PercentageTest {

    // ---------------------------------------------------------------------
    // Construction and equivalence
    // ---------------------------------------------------------------------

    @Test
    void shouldConsiderPercentAndEquivalentFractionEqual() {
        Percentage fromPercent = Percentage.ofPercent(10);
        Percentage fromFraction = Percentage.ofFraction(new BigDecimal("0.10"));

        assertThat(fromPercent).isEqualTo(fromFraction);
    }

    @Test
    void shouldAcceptDecimalPercentGivenAsString() {
        assertThatCode(() -> Percentage.ofPercent("12.5")).doesNotThrowAnyException();
    }

    @Test
    void shouldConsiderDecimalPercentGivenAsStringEqualToEquivalentFraction() {
        Percentage fromString = Percentage.ofPercent("12.5");
        Percentage fromFraction = Percentage.ofFraction(new BigDecimal("0.125"));

        assertThat(fromString).isEqualTo(fromFraction);
    }

    @Test
    void shouldConsiderDecimalPercentGivenAsBigDecimalEqualToSamePercentGivenAsString() {
        Percentage fromBigDecimal = Percentage.ofPercent(new BigDecimal("12.5"));
        Percentage fromString = Percentage.ofPercent("12.5");

        assertThat(fromBigDecimal).isEqualTo(fromString);
    }

    @Test
    void shouldAcceptPercentAboveOneHundred() {
        assertThatCode(() -> Percentage.ofPercent(150)).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------------
    // Upper limit: NUMERIC(5,4), largest fraction 9.9999 (999.99%)
    // ---------------------------------------------------------------------

    @Test
    void shouldAcceptPercentAtExactUpperLimitOfNumeric5Scale4() {
        assertThatCode(() -> Percentage.ofPercent("999.99")).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectPercentAboveUpperLimitWithInvalidPercentageCode() {
        assertThatThrownBy(() -> Percentage.ofPercent("1000"))
                .asInstanceOf(type(InvalidPercentageException.class))
                .extracting(InvalidPercentageException::code)
                .isEqualTo("INVALID_PERCENTAGE");
    }

    // ---------------------------------------------------------------------
    // Negative percentage
    // ---------------------------------------------------------------------

    @Test
    void shouldRejectNegativeIntegerPercent() {
        assertThatThrownBy(() -> Percentage.ofPercent(-1))
                .isInstanceOf(InvalidPercentageException.class);
    }

    @Test
    void shouldRejectNegativeBigDecimalPercent() {
        assertThatThrownBy(() -> Percentage.ofPercent(new BigDecimal("-12.5")))
                .isInstanceOf(InvalidPercentageException.class);
    }

    @Test
    void shouldRejectNegativeStringPercent() {
        assertThatThrownBy(() -> Percentage.ofPercent("-12.5"))
                .isInstanceOf(InvalidPercentageException.class);
    }

    @Test
    void shouldRejectNegativeFraction() {
        assertThatThrownBy(() -> Percentage.ofFraction(new BigDecimal("-0.10")))
                .isInstanceOf(InvalidPercentageException.class);
    }

    // ---------------------------------------------------------------------
    // applyTo(Money)
    // ---------------------------------------------------------------------

    @Test
    void shouldRoundHalfUpWhenTenPercentIsAppliedTo1225() {
        Percentage tenPercent = Percentage.ofPercent(10);

        Money result = tenPercent.applyTo(Money.of("12.25"));

        assertThat(result).isEqualTo(Money.of("1.23"));
    }

    @Test
    void shouldRoundHalfUpWhenFiftyPercentIsAppliedToOneCent() {
        Percentage fiftyPercent = Percentage.ofPercent(50);

        Money result = fiftyPercent.applyTo(Money.of("0.01"));

        assertThat(result).isEqualTo(Money.of("0.01"));
    }

    @Test
    void shouldReturnZeroWhenZeroPercentIsApplied() {
        Percentage zeroPercent = Percentage.ofPercent(0);

        Money result = zeroPercent.applyTo(Money.of("123.45"));

        assertThat(result.isZero()).isTrue();
    }
}
