package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
    void shouldAcceptTrailingZeroBeyondSecondDecimalPlace() {
        assertThatCode(() -> Money.of("0.010")).doesNotThrowAnyException();
    }

    @Test
    void shouldConsiderAmountWithTrailingZeroBeyondSecondDecimalEqualToTwoDecimalAmount() {
        Money withTrailingZero = Money.of("0.010");
        Money withTwoDecimals = Money.of("0.01");

        assertThat(withTrailingZero).isEqualTo(withTwoDecimals);
    }

    @Test
    void shouldRejectScientificNotationWithInvalidMoneyCode() {
        assertThatThrownBy(() -> Money.of("1E+3"))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("INVALID_MONEY");
    }

    /** NUMERIC(12,2): largest representable amount. */
    @Test
    void shouldAcceptAmountAtExactUpperLimitOfNumeric12Scale2() {
        assertThatCode(() -> Money.of("9999999999.99")).doesNotThrowAnyException();
    }

    /** One cent above the NUMERIC(12,2) limit. */
    @Test
    void shouldRejectAmountOneCentAboveUpperLimitWithOutOfRangeCode() {
        assertThatThrownBy(() -> Money.of("10000000000.00"))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("MONEY_OUT_OF_RANGE");
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

    @Test
    void shouldStripSurroundingWhitespaceBeforeValidatingAmount() {
        Money withSurroundingWhitespace = Money.of(" 10.00 ");

        assertThat(withSurroundingWhitespace).isEqualTo(Money.of("10.00"));
    }

    @Test
    void shouldAcceptExplicitPositiveSignAsSameAmountWithoutSign() {
        Money withPositiveSign = Money.of("+10.00");

        assertThat(withPositiveSign).isEqualTo(Money.of("10.00"));
    }

    @Test
    void shouldRejectAmountWithoutIntegerPartWithInvalidMoneyCode() {
        assertThatThrownBy(() -> Money.of(".5"))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("INVALID_MONEY");
    }

    @Test
    void shouldRejectAmountWithDecimalPointButNoFractionDigitsWithInvalidMoneyCode() {
        assertThatThrownBy(() -> Money.of("5."))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("INVALID_MONEY");
    }

    // ---------------------------------------------------------------------
    // Multiplication factor guard
    // ---------------------------------------------------------------------

    /**
     * A factor with absurd precision must be rejected up front. Without the guard,
     * rescaling the product can hang the CPU; the timeout runs in a separate thread
     * so a missing guard fails the test instead of freezing the build.
     */
    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void shouldRejectMultiplicationFactorWithAbsurdPrecisionWithInvalidMoneyCode() {
        Money amount = Money.of("10.00");
        BigDecimal absurdPrecisionFactor = new BigDecimal("1E-999999999");

        assertThatThrownBy(() -> amount.multiply(absurdPrecisionFactor))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("INVALID_MONEY");
    }

    /**
     * 1 x 10^2147483648: the product's scale falls outside the int range. Must surface as
     * the domain exception, not ArithmeticException.
     */
    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void shouldRejectMultiplicationFactorWithScaleAtIntegerMinValueWithInvalidMoneyCode() {
        Money amount = Money.of("10.00");
        BigDecimal factorWithMinimumScale = new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE);

        assertThatThrownBy(() -> amount.multiply(factorWithMinimumScale))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("INVALID_MONEY");
    }

    // ---------------------------------------------------------------------
    // Denial of service: absurd inputs rejected before any expensive work
    // ---------------------------------------------------------------------

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void shouldRejectBigDecimalAmountWithAstronomicalExponentWithInvalidMoneyCode() {
        BigDecimal astronomicalAmount = new BigDecimal("1E+999999999");

        assertThatThrownBy(() -> Money.of(astronomicalAmount))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("INVALID_MONEY");
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void shouldRejectAmountTextWithTwoHundredThousandTrailingZerosWithInvalidMoneyCode() {
        String giantAmountText = "1." + "0".repeat(200_000);

        assertThatThrownBy(() -> Money.of(giantAmountText))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("INVALID_MONEY");
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void shouldNotEchoRejectedAstronomicalAmountInExceptionMessage() {
        BigDecimal astronomicalAmount = new BigDecimal("1E+999999999");

        assertThatThrownBy(() -> Money.of(astronomicalAmount))
                .isInstanceOf(InvalidMoneyException.class)
                .extracting(Throwable::getMessage, STRING)
                .doesNotContain("1E+999999999")
                .hasSizeLessThan(200);
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void shouldNotEchoRejectedGiantAmountTextInExceptionMessage() {
        String giantAmountText = "1." + "0".repeat(200_000);

        assertThatThrownBy(() -> Money.of(giantAmountText))
                .isInstanceOf(InvalidMoneyException.class)
                .extracting(Throwable::getMessage, STRING)
                .doesNotContain(giantAmountText)
                .hasSizeLessThan(200);
    }

    // ---------------------------------------------------------------------
    // Text length limit: 25 characters after stripping surrounding whitespace
    // ---------------------------------------------------------------------

    /** "10." + 22 zeros = 25 characters; trailing zeros beyond the second decimal are accepted. */
    @Test
    void shouldAcceptAmountTextWithExactly25Characters() {
        String textWith25Characters = "10." + "0".repeat(22);

        Money result = Money.of(textWith25Characters);

        assertThat(result).isEqualTo(Money.of("10.00"));
    }

    /** "10." + 23 zeros = 26 characters, otherwise valid. */
    @Test
    void shouldRejectAmountTextWith26CharactersWithInvalidMoneyCode() {
        String textWith26Characters = "10." + "0".repeat(23);

        assertThatThrownBy(() -> Money.of(textWith26Characters))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("INVALID_MONEY");
    }

    /** 25 significant characters plus surrounding whitespace: length counted after stripping. */
    @Test
    void shouldNotCountSurroundingWhitespaceTowardsAmountTextLengthLimit() {
        String textWith25CharactersAndSurroundingWhitespace = "   10." + "0".repeat(22) + "   ";

        Money result = Money.of(textWith25CharactersAndSurroundingWhitespace);

        assertThat(result).isEqualTo(Money.of("10.00"));
    }

    // ---------------------------------------------------------------------
    // Malformed text
    // ---------------------------------------------------------------------

    @Test
    void shouldRejectAmountWithCombinedSignsWithInvalidMoneyCode() {
        assertThatThrownBy(() -> Money.of("+-10"))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("INVALID_MONEY");
    }

    @Test
    void shouldRejectBlankAmountTextWithInvalidMoneyCode() {
        assertThatThrownBy(() -> Money.of("   "))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("INVALID_MONEY");
    }

    // ---------------------------------------------------------------------
    // Lower limit: NUMERIC(12,2) is symmetric
    // ---------------------------------------------------------------------

    @Test
    void shouldAcceptAmountAtExactLowerLimitOfNumeric12Scale2() {
        assertThatCode(() -> Money.of("-9999999999.99")).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectAmountOneCentBelowLowerLimitWithOutOfRangeCode() {
        assertThatThrownBy(() -> Money.of("-10000000000.00"))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("MONEY_OUT_OF_RANGE");
    }

    // ---------------------------------------------------------------------
    // Overflow produced by operations
    // ---------------------------------------------------------------------

    @Test
    void shouldAcceptAdditionReachingExactUpperLimit() {
        Money result = Money.of("9999999999.98").plus(Money.of("0.01"));

        assertThat(result).isEqualTo(Money.of("9999999999.99"));
    }

    @Test
    void shouldRejectAdditionExceedingUpperLimitWithOutOfRangeCode() {
        Money atUpperLimit = Money.of("9999999999.99");

        assertThatThrownBy(() -> atUpperLimit.plus(Money.of("0.01")))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("MONEY_OUT_OF_RANGE");
    }

    @Test
    void shouldRejectSubtractionExceedingLowerLimitWithOutOfRangeCode() {
        Money atLowerLimit = Money.of("-9999999999.99");

        assertThatThrownBy(() -> atLowerLimit.minus(Money.of("0.01")))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("MONEY_OUT_OF_RANGE");
    }

    @Test
    void shouldRejectIntegerMultiplicationExceedingUpperLimitWithOutOfRangeCode() {
        Money atUpperLimit = Money.of("9999999999.99");

        assertThatThrownBy(() -> atUpperLimit.multiply(2))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("MONEY_OUT_OF_RANGE");
    }

    @Test
    void shouldRejectPercentageExceedingUpperLimitWithOutOfRangeCode() {
        Money atUpperLimit = Money.of("9999999999.99");
        Percentage twoHundredPercent = Percentage.ofPercent(200);

        assertThatThrownBy(() -> atUpperLimit.percentage(twoHundredPercent))
                .asInstanceOf(type(InvalidMoneyException.class))
                .extracting(InvalidMoneyException::code)
                .isEqualTo("MONEY_OUT_OF_RANGE");
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
