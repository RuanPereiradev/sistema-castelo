package br.com.castel.sharedkernel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monetary amount in the property's single currency, always with two decimal places.
 *
 * <p>Construction is exact: an amount that would need rounding to fit two decimal places is
 * rejected with {@link InvalidMoneyException#MONEY_SCALE_EXCEEDED}. Operations round
 * {@link RoundingMode#HALF_UP}, which moves ties away from zero for negative amounts too.
 *
 * <p>A null argument, wherever a {@code Money} or a multiplication factor is expected, is rejected
 * with {@link InvalidMoneyException#INVALID_MONEY}.
 */
public final class Money {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final Money ZERO = new Money(BigDecimal.ZERO.setScale(SCALE));

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount;
    }

    public static Money of(BigDecimal amount) {
        if (amount == null) {
            throw InvalidMoneyException.invalid("Amount must not be null");
        }
        try {
            return new Money(amount.setScale(SCALE, RoundingMode.UNNECESSARY));
        } catch (ArithmeticException exception) {
            throw InvalidMoneyException.scaleExceeded(amount);
        }
    }

    public static Money of(String amount) {
        if (amount == null) {
            throw InvalidMoneyException.invalid("Amount must not be null");
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(amount);
        } catch (NumberFormatException exception) {
            throw InvalidMoneyException.invalid("Amount is not a decimal number: " + amount);
        }
        return of(parsed);
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
