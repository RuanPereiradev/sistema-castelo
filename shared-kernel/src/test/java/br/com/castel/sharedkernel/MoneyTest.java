package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MoneyTest {

    // ---------------------------------------------------------------------
    // Rounding: each case below distinguishes HALF_UP from HALF_EVEN
    // ---------------------------------------------------------------------

    @Test
    void shouldRoundTenPercentOf1225HalfUpTo123() {
        Money amount = Money.of("12.25");

        Money result = amount.percentage(Percentage.ofPercent(10));

        assertThat(result).isEqualTo(Money.of("1.23"));
    }

    @Test
    void shouldRoundFiftyPercentOfOneCentHalfUpToOneCent() {
        Money amount = Money.of("0.01");

        Money result = amount.percentage(Percentage.ofPercent(50));

        assertThat(result).isEqualTo(Money.of("0.01"));
    }

    @Test
    void shouldRoundMultiplicationOf1005ByHalfHalfUpTo503() {
        Money amount = Money.of("10.05");

        Money result = amount.multiply(new BigDecimal("0.5"));

        assertThat(result).isEqualTo(Money.of("5.03"));
    }

    @Test
    void shouldRoundNegativeHalfCentAwayFromZeroWhenApplyingPercentage() {
        Money amount = Money.of("-0.01");

        Money result = amount.percentage(Percentage.ofPercent(50));

        assertThat(result).isEqualTo(Money.of("-0.01"));
    }

    @Test
    void shouldRoundNegativeHalfCentAwayFromZeroWhenMultiplyingByDecimal() {
        Money amount = Money.of("-10.05");

        Money result = amount.multiply(new BigDecimal("0.5"));

        assertThat(result).isEqualTo(Money.of("-5.03"));
    }

    @Test
    void shouldRoundMultiplicationByDecimalToTwoDecimalPlaces() {
        Money amount = Money.of("10.00");

        Money result = amount.multiply(new BigDecimal("0.333"));

        assertThat(result).isEqualTo(Money.of("3.33"));
    }

    @Test
    void shouldNotAccumulatePrecisionErrorWhenAddingValuesThatAreInexactInBinary() {
        Money result = Money.of("0.10").plus(Money.of("0.20"));

        assertThat(result).isEqualTo(Money.of("0.30"));
    }

    @Test
    void shouldNotAccumulatePrecisionErrorAcrossChainOfTenAdditions() {
        Money result = Stream.generate(() -> Money.of("0.10"))
                .limit(10)
                .reduce(Money.ZERO, Money::plus);

        assertThat(result).isEqualTo(Money.of("1.00"));
    }

    @Test
    void shouldNotAccumulatePrecisionErrorAcrossMixedOperations() {
        Money result = Money.of("19.90")
                .multiply(3)
                .plus(Money.of("0.30"))
                .minus(Money.of("59.70"));

        assertThat(result).isEqualTo(Money.of("0.30"));
    }

    // ---------------------------------------------------------------------
    // Immutability
    // ---------------------------------------------------------------------

    @Test
    void shouldReturnNewInstanceAndKeepOriginalIntactWhenAdding() {
        Money original = Money.of("10.00");

        Money result = original.plus(Money.of("5.00"));

        assertThat(result).isEqualTo(Money.of("15.00"));
        assertThat(original).isEqualTo(Money.of("10.00"));
    }

    @Test
    void shouldReturnNewInstanceAndKeepOriginalIntactWhenSubtracting() {
        Money original = Money.of("10.00");

        Money result = original.minus(Money.of("4.00"));

        assertThat(result).isEqualTo(Money.of("6.00"));
        assertThat(original).isEqualTo(Money.of("10.00"));
    }

    @Test
    void shouldReturnNewInstanceAndKeepOriginalIntactWhenMultiplyingByInteger() {
        Money original = Money.of("10.00");

        Money result = original.multiply(3);

        assertThat(result).isEqualTo(Money.of("30.00"));
        assertThat(original).isEqualTo(Money.of("10.00"));
    }

    @Test
    void shouldReturnNewInstanceAndKeepOriginalIntactWhenMultiplyingByDecimal() {
        Money original = Money.of("10.00");

        Money result = original.multiply(new BigDecimal("2.5"));

        assertThat(result).isEqualTo(Money.of("25.00"));
        assertThat(original).isEqualTo(Money.of("10.00"));
    }

    @Test
    void shouldReturnNewInstanceAndKeepOriginalIntactWhenApplyingPercentage() {
        Money original = Money.of("200.00");

        Money result = original.percentage(Percentage.ofPercent(10));

        assertThat(result).isEqualTo(Money.of("20.00"));
        assertThat(original).isEqualTo(Money.of("200.00"));
    }

    @Test
    void shouldReturnNewInstanceAndKeepOriginalIntactWhenNegating() {
        Money original = Money.of("10.00");

        Money result = original.negate();

        assertThat(result).isEqualTo(Money.of("-10.00"));
        assertThat(original).isEqualTo(Money.of("10.00"));
    }

    @Test
    void shouldReturnNewInstanceAndKeepOriginalIntactWhenTakingAbsoluteValue() {
        Money original = Money.of("-10.00");

        Money result = original.abs();

        assertThat(result).isEqualTo(Money.of("10.00"));
        assertThat(original).isEqualTo(Money.of("-10.00"));
    }

    // ---------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------

    @Test
    void shouldConsiderAmountWithoutDecimalsEqualToSameAmountWithTwoDecimals() {
        Money withoutDecimals = Money.of("10");
        Money withTwoDecimals = Money.of("10.00");

        assertThat(withoutDecimals).isEqualTo(withTwoDecimals);
    }

    @Test
    void shouldAcceptAmountWithExactlyTwoDecimalPlaces() {
        assertThatCode(() -> Money.of("0.01")).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectAmountWithMoreThanTwoDecimalPlacesWithScaleExceededCode() {
        assertThatThrownBy(() -> Money.of("0.001"))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("MONEY_SCALE_EXCEEDED");
    }

    @Test
    void shouldRejectNullAmountWithInvalidMoneyException() {
        assertThatThrownBy(() -> Money.of((String) null))
                .isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    void shouldRejectNonNumericAmountWithInvalidMoneyException() {
        assertThatThrownBy(() -> Money.of("abc"))
                .isInstanceOf(InvalidMoneyException.class);
    }

    // ---------------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------------

    @Test
    void shouldReportZeroConstantAsZero() {
        assertThat(Money.ZERO.isZero()).isTrue();
    }

    @Test
    void shouldAllowCreatingNegativeAmount() {
        assertThatCode(() -> Money.of("-10.00")).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowSubtractionResultingInNegativeAmount() {
        Money result = Money.of("5.00").minus(Money.of("8.00"));

        assertThat(result).isEqualTo(Money.of("-3.00"));
    }
}
