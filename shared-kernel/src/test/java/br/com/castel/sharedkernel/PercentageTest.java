package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PercentageTest {

    @Test
    void shouldConsiderPercentAndEquivalentFractionEqual() {
        Percentage fromPercent = Percentage.ofPercent(10);
        Percentage fromFraction = Percentage.ofFraction(new BigDecimal("0.10"));

        assertThat(fromPercent).isEqualTo(fromFraction);
    }

    @Test
    void shouldRoundHalfUpWhenAppliedToMoney() {
        Percentage tenPercent = Percentage.ofPercent(10);

        Money result = tenPercent.applyTo(Money.of("12.25"));

        assertThat(result).isEqualTo(Money.of("1.23"));
    }

    @Test
    void shouldRejectNegativePercent() {
        assertThatThrownBy(() -> Percentage.ofPercent(-1))
                .isInstanceOf(InvalidPercentageException.class);
    }

    @Test
    void shouldRejectNegativeFraction() {
        assertThatThrownBy(() -> Percentage.ofFraction(new BigDecimal("-0.10")))
                .isInstanceOf(InvalidPercentageException.class);
    }

    @Test
    void shouldReturnZeroWhenZeroPercentIsApplied() {
        Percentage zeroPercent = Percentage.ofPercent(0);

        Money result = zeroPercent.applyTo(Money.of("123.45"));

        assertThat(result.isZero()).isTrue();
    }
}
