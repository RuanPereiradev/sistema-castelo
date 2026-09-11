package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");

    // ---------------------------------------------------------------------
    // Rounding
    // ---------------------------------------------------------------------

    @Test
    void shouldRoundTenPercentOf1235To124() {
        Money amount = Money.of("12.35");

        Money result = amount.percentage(Percentage.ofPercent(10));

        assertThat(result).isEqualTo(Money.of("1.24"));
    }

    @Test
    void shouldRoundTenPercentOf1225HalfUpTo123InsteadOfHalfEven() {
        Money amount = Money.of("12.25");

        Money result = amount.percentage(Percentage.ofPercent(10));

        assertThat(result).isEqualTo(Money.of("1.23"));
    }

    @Test
    void shouldRoundHalfCentUpToOneCent() {
        Money result = Money.of("0.005");

        assertThat(result).isEqualTo(Money.of("0.01"));
    }

    @Test
    void shouldRoundMultiplicationByDecimalToTwoDecimalPlaces() {
        Money amount = Money.of("10.00");

        Money result = amount.multiply(new BigDecimal("0.333"));

        assertThat(result.amount()).isEqualTo(new BigDecimal("3.33"));
    }

    @Test
    void shouldRoundMultiplicationByDecimalHalfUp() {
        Money amount = Money.of("10.05");

        Money result = amount.multiply(new BigDecimal("0.5"));

        assertThat(result).isEqualTo(Money.of("5.03"));
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
                .multiply(new BigDecimal("3"))
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
    void shouldReturnNewInstanceAndKeepOriginalIntactWhenMultiplying() {
        Money original = Money.of("10.00");

        Money result = original.multiply(new BigDecimal("2"));

        assertThat(result).isEqualTo(Money.of("20.00"));
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
    // Currency
    // ---------------------------------------------------------------------

    @Test
    void shouldRejectAddingMoneyInDifferentCurrencies() {
        Money brl = Money.of("10.00");
        Money usd = Money.of("10.00", USD);

        assertThatThrownBy(() -> brl.plus(usd))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void shouldNotConsiderSameAmountInDifferentCurrenciesEqual() {
        Money brl = Money.of("10.00");
        Money usd = Money.of("10.00", USD);

        assertThat(brl).isNotEqualTo(usd);
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

    @Test
    void shouldRoundAmountBelowHalfCentDownToZero() {
        Money result = Money.of("0.001");

        assertThat(result.isZero()).isTrue();
    }

    @Test
    void shouldRejectNullAmount() {
        assertThatThrownBy(() -> Money.of((String) null))
                .isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    void shouldRejectNonNumericAmount() {
        assertThatThrownBy(() -> Money.of("abc"))
                .isInstanceOf(InvalidMoneyException.class);
    }
}
