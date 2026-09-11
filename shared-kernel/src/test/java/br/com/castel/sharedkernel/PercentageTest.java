package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
    void shouldRejectScientificNotationPercentWithInvalidPercentageCode() {
        assertThatThrownBy(() -> Percentage.ofPercent("1E+2"))
                .asInstanceOf(type(InvalidPercentageException.class))
                .extracting(InvalidPercentageException::code)
                .isEqualTo("INVALID_PERCENTAGE");
    }

    @Test
    void shouldStripSurroundingWhitespaceBeforeValidatingPercent() {
        Percentage withSurroundingWhitespace = Percentage.ofPercent(" 12.5 ");

        assertThat(withSurroundingWhitespace).isEqualTo(Percentage.ofPercent("12.5"));
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

    @Test
    void shouldAcceptFractionAtExactUpperLimitOfNumeric5Scale4() {
        assertThatCode(() -> Percentage.ofFraction(new BigDecimal("9.9999"))).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectFractionOneTenThousandthAboveUpperLimitWithInvalidPercentageCode() {
        assertThatThrownBy(() -> Percentage.ofFraction(new BigDecimal("10")))
                .asInstanceOf(type(InvalidPercentageException.class))
                .extracting(InvalidPercentageException::code)
                .isEqualTo("INVALID_PERCENTAGE");
    }

    // ---------------------------------------------------------------------
    // Absurd scale: domain exception, not ArithmeticException
    // ---------------------------------------------------------------------

    /** 1 x 10^-2147483647: converting percent to fraction would overflow the int scale. */
    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void shouldRejectPercentWithScaleAtIntegerMaxValueWithInvalidPercentageCode() {
        BigDecimal percentWithMaximumScale = new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE);

        assertThatThrownBy(() -> Percentage.ofPercent(percentWithMaximumScale))
                .asInstanceOf(type(InvalidPercentageException.class))
                .extracting(InvalidPercentageException::code)
                .isEqualTo("INVALID_PERCENTAGE");
    }

    // ---------------------------------------------------------------------
    // Malformed text
    // ---------------------------------------------------------------------

    /**
     * 26 characters. Whether trailing zeros beyond scale are accepted for Percentage is not
     * specified, so this text may also be rejected by another rule (see report).
     */
    @Test
    void shouldRejectPercentTextWith26CharactersWithInvalidPercentageCode() {
        String textWith26Characters = "10." + "0".repeat(23);

        assertThatThrownBy(() -> Percentage.ofPercent(textWith26Characters))
                .asInstanceOf(type(InvalidPercentageException.class))
                .extracting(InvalidPercentageException::code)
                .isEqualTo("INVALID_PERCENTAGE");
    }

    /** Arabic-Indic digits ONE and TWO ("12"), accepted by Character.isDigit but not ASCII. */
    @Test
    void shouldRejectPercentWrittenWithArabicIndicDigitsWithInvalidPercentageCode() {
        String arabicIndicTwelve = "١٢";

        assertThatThrownBy(() -> Percentage.ofPercent(arabicIndicTwelve))
                .asInstanceOf(type(InvalidPercentageException.class))
                .extracting(InvalidPercentageException::code)
                .isEqualTo("INVALID_PERCENTAGE");
    }

    @Test
    void shouldRejectPercentWithoutIntegerPartWithInvalidPercentageCode() {
        assertThatThrownBy(() -> Percentage.ofPercent(".5"))
                .asInstanceOf(type(InvalidPercentageException.class))
                .extracting(InvalidPercentageException::code)
                .isEqualTo("INVALID_PERCENTAGE");
    }

    @Test
    void shouldRejectPercentWithDecimalPointButNoFractionDigitsWithInvalidPercentageCode() {
        assertThatThrownBy(() -> Percentage.ofPercent("5."))
                .asInstanceOf(type(InvalidPercentageException.class))
                .extracting(InvalidPercentageException::code)
                .isEqualTo("INVALID_PERCENTAGE");
    }

    @Test
    void shouldRejectPercentWithCombinedSignsWithInvalidPercentageCode() {
        assertThatThrownBy(() -> Percentage.ofPercent("+-10"))
                .asInstanceOf(type(InvalidPercentageException.class))
                .extracting(InvalidPercentageException::code)
                .isEqualTo("INVALID_PERCENTAGE");
    }

    @Test
    void shouldRejectBlankPercentTextWithInvalidPercentageCode() {
        assertThatThrownBy(() -> Percentage.ofPercent("   "))
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
