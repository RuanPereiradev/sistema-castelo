package br.com.castel.sharedkernel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Monetary amount in the property's single currency, always with two decimal places.
 *
 * <p>Construction is exact: an amount that would need rounding to fit two decimal places is
 * rejected with {@link InvalidMoneyException#MONEY_SCALE_EXCEEDED}. Operations round
 * {@link RoundingMode#HALF_UP}, which moves ties away from zero for negative amounts too.
 *
 * <p>Every instance, including the result of any operation, fits a {@code NUMERIC(12,2)} column:
 * from {@code -9999999999.99} to {@code 9999999999.99}. Anything outside is rejected with
 * {@link InvalidMoneyException#MONEY_OUT_OF_RANGE}.
 *
 * <p>Text input is stripped of surrounding whitespace ({@link String#strip()}) and must then be a
 * plain decimal: an optional leading sign ({@code +} or {@code -}), ASCII digits and an optional
 * fraction with at least one digit. Scientific notation, a missing integer part ({@code ".5"}), a
 * dangling point ({@code "5."}) and blank text are rejected with
 * {@link InvalidMoneyException#INVALID_MONEY}. When text breaks more than one rule, the code follows
 * the order format, then scale, then range.
 *
 * <p>A decimal multiplication factor must have a scale between {@code -16} and {@code 16} and at most
 * 32 significant digits; anything beyond that is rejected with
 * {@link InvalidMoneyException#INVALID_MONEY} before any arithmetic, so a pathological factor cannot
 * stall the rescaling of the product.
 *
 * <p>A null argument, wherever a {@code Money} or a multiplication factor is expected, is rejected
 * with {@link InvalidMoneyException#INVALID_MONEY}.
 */
public final class Money {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal MAXIMUM_MAGNITUDE = new BigDecimal("9999999999.99");
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("[+-]?[0-9]+(\\.[0-9]+)?");
    private static final int MAXIMUM_FACTOR_SCALE = 16;
    private static final int MAXIMUM_FACTOR_PRECISION = 32;

    public static final Money ZERO = new Money(BigDecimal.ZERO.setScale(SCALE));

    private final BigDecimal amount;

    /** Receives an amount already at scale 2. */
    private Money(BigDecimal amount) {
        this.amount = requireWithinRange(amount);
    }

    public static Money of(BigDecimal amount) {
        if (amount == null) {
            throw InvalidMoneyException.invalid("Amount must not be null");
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw InvalidMoneyException.scaleExceeded(amount);
        }
        return new Money(requireWithinRange(amount).setScale(SCALE, RoundingMode.UNNECESSARY));
    }

    public static Money of(String amount) {
        if (amount == null) {
            throw InvalidMoneyException.invalid("Amount must not be null");
        }
        String stripped = amount.strip();
        if (!PLAIN_DECIMAL.matcher(stripped).matches()) {
            throw InvalidMoneyException.invalid("Amount is not a plain decimal number: " + amount);
        }
        return of(new BigDecimal(stripped));
    }

    public static Money ofCents(long cents) {
        return new Money(BigDecimal.valueOf(cents, SCALE));
    }

    public Money plus(Money other) {
        return new Money(amount.add(requireMoney(other).amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(requireMoney(other).amount));
    }

    public Money multiply(int factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)));
    }

    public Money multiply(BigDecimal factor) {
        if (factor == null) {
            throw InvalidMoneyException.invalid("Multiplication factor must not be null");
        }
        if (Math.abs(factor.scale()) > MAXIMUM_FACTOR_SCALE || factor.precision() > MAXIMUM_FACTOR_PRECISION) {
            throw InvalidMoneyException.invalid("Multiplication factor exceeds scale "
                    + MAXIMUM_FACTOR_SCALE + " or precision " + MAXIMUM_FACTOR_PRECISION);
        }
        return new Money(amount.multiply(factor).setScale(SCALE, ROUNDING));
    }

    public Money percentage(Percentage percentage) {
        if (percentage == null) {
            throw new InvalidPercentageException("Percentage must not be null");
        }
        return percentage.applyTo(this);
    }

    public Money negate() {
        return new Money(amount.negate());
    }

    public Money abs() {
        return new Money(amount.abs());
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isGreaterThan(Money other) {
        return amount.compareTo(requireMoney(other).amount) > 0;
    }

    public boolean isLessThan(Money other) {
        return amount.compareTo(requireMoney(other).amount) < 0;
    }

    /** The amount with scale 2. */
    public BigDecimal amount() {
        return amount;
    }

    /** Plain decimal with two places, as it travels in the API: {@code "180.00"}. */
    public String asString() {
        return amount.toPlainString();
    }

    /** Exact for amounts with at most two decimal places, which is all this class ever checks. */
    private static BigDecimal requireWithinRange(BigDecimal amount) {
        if (amount.abs().compareTo(MAXIMUM_MAGNITUDE) > 0) {
            throw InvalidMoneyException.outOfRange(amount.toPlainString(), MAXIMUM_MAGNITUDE);
        }
        return amount;
    }

    private static Money requireMoney(Money money) {
        if (money == null) {
            throw InvalidMoneyException.invalid("Money must not be null");
        }
        return money;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Money money && amount.equals(money.amount);
    }

    @Override
    public int hashCode() {
        return amount.hashCode();
    }

    @Override
    public String toString() {
        return asString();
    }
}
