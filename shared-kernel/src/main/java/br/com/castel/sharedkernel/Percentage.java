package br.com.castel.sharedkernel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Non-negative rate, such as the service charge, stored as a fraction with four decimal places
 * ({@code 0.1000} is 10%), matching a {@code NUMERIC(5,4)} column.
 *
 * <p>Values from 0% to 999.99% (fraction {@code 9.9999}) are accepted, so rates above 100% are valid.
 * Construction is exact: a rate finer than four fraction places (e.g. 12.345%) is rejected instead
 * of silently rounded.
 */
public final class Percentage {

    private static final int FRACTION_SCALE = 4;
    private static final int PERCENT_TO_FRACTION_SHIFT = 2;
    private static final BigDecimal MAXIMUM_FRACTION = new BigDecimal("9.9999");

    private final BigDecimal fraction;

    private Percentage(BigDecimal fraction) {
        this.fraction = fraction;
    }

    public static Percentage ofFraction(BigDecimal fraction) {
        if (fraction == null) {
            throw new InvalidPercentageException("Fraction must not be null");
        }
        if (fraction.signum() < 0) {
            throw new InvalidPercentageException("Percentage must not be negative: " + fraction);
        }
        if (fraction.compareTo(MAXIMUM_FRACTION) > 0) {
            throw new InvalidPercentageException(
                    "Fraction must not exceed " + MAXIMUM_FRACTION + ": " + fraction);
        }
        try {
            return new Percentage(fraction.setScale(FRACTION_SCALE, RoundingMode.UNNECESSARY));
        } catch (ArithmeticException exception) {
            throw new InvalidPercentageException(
                    "Fraction has more than " + FRACTION_SCALE + " decimal places: " + fraction);
        }
    }

    public static Percentage ofPercent(int percent) {
        return ofPercent(BigDecimal.valueOf(percent));
    }

    public static Percentage ofPercent(BigDecimal percent) {
        if (percent == null) {
            throw new InvalidPercentageException("Percent must not be null");
        }
        return ofFraction(percent.movePointLeft(PERCENT_TO_FRACTION_SHIFT));
    }

    public static Percentage ofPercent(String percent) {
        if (percent == null) {
            throw new InvalidPercentageException("Percent must not be null");
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(percent);
        } catch (NumberFormatException exception) {
            throw new InvalidPercentageException("Percent is not a decimal number: " + percent);
        }
        return ofPercent(parsed);
    }

    /** The share of {@code money} this percentage represents, rounded HALF_UP to two decimal places. */
    public Money applyTo(Money money) {
        if (money == null) {
            throw InvalidMoneyException.invalid("Money must not be null");
        }
        return money.multiply(fraction);
    }

    /** The rate as a fraction with scale 4: {@code 0.1000} for 10%. */
    public BigDecimal fraction() {
        return fraction;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Percentage percentage && fraction.equals(percentage.fraction);
    }

    @Override
    public int hashCode() {
        return fraction.hashCode();
    }

    @Override
    public String toString() {
        return fraction.movePointRight(PERCENT_TO_FRACTION_SHIFT).stripTrailingZeros().toPlainString() + "%";
    }
}
