package br.com.castel.sharedkernel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Positive weight in whole grams, from 1 to 50000, matching a {@code weight_grams INTEGER} column.
 *
 * <p>Construction is exact: a value in kilos that is not a whole number of grams is rejected. The
 * upper bound applies to both factories. A value in kilos whose scale lies outside {@code -100..100}
 * or whose precision exceeds 100 digits is rejected before any conversion.
 *
 * <p>Every rejection is an {@link InvalidWeightException}; its message never includes the rejected value.
 */
public final class Weight {

    private static final int GRAMS_PER_KILO_EXPONENT = 3;
    private static final int MAXIMUM_GRAMS = 50_000;
    private static final BigDecimal MAXIMUM_KILOS = BigDecimal.valueOf(MAXIMUM_GRAMS, GRAMS_PER_KILO_EXPONENT);

    private final int grams;

    private Weight(int grams) {
        this.grams = grams;
    }

    public static Weight ofGrams(int grams) {
        if (grams <= 0) {
            throw new InvalidWeightException("Weight must be positive");
        }
        if (grams > MAXIMUM_GRAMS) {
            throw new InvalidWeightException("Weight must not exceed " + MAXIMUM_GRAMS + " grams");
        }
        return new Weight(grams);
    }

    public static Weight ofKilos(BigDecimal kilos) {
        if (kilos == null) {
            throw new InvalidWeightException("Kilos must not be null");
        }
        DecimalInput.requireBounded(kilos, InvalidWeightException::new);
        if (kilos.signum() <= 0) {
            throw new InvalidWeightException("Weight must be positive");
        }
        if (kilos.compareTo(MAXIMUM_KILOS) > 0) {
            throw new InvalidWeightException("Weight must not exceed " + MAXIMUM_GRAMS + " grams");
        }
        try {
            return ofGrams(kilos.movePointRight(GRAMS_PER_KILO_EXPONENT)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .intValueExact());
        } catch (ArithmeticException exception) {
            throw new InvalidWeightException("Weight is not a whole number of grams");
        }
    }

    public int grams() {
        return grams;
    }

    /** Weight in kilos with scale 3: {@code 0.437} for 437 grams. */
    public BigDecimal kilos() {
        return BigDecimal.valueOf(grams, GRAMS_PER_KILO_EXPONENT);
    }

    /**
     * Price of this weight at the given price per kilo, rounded HALF_UP to two decimal places.
     *
     * @throws InvalidMoneyException with code {@code INVALID_MONEY} if the price per kilo is null,
     *     zero or negative
     */
    public Money priceAt(Money pricePerKilo) {
        if (pricePerKilo == null) {
            throw InvalidMoneyException.invalid("Price per kilo must not be null");
        }
        if (!pricePerKilo.isPositive()) {
            throw InvalidMoneyException.invalid("Price per kilo must be positive");
        }
        return pricePerKilo.multiply(kilos());
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Weight weight && grams == weight.grams;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(grams);
    }

    @Override
    public String toString() {
        return grams + "g";
    }
}
