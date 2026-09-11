package br.com.castel.sharedkernel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Positive weight in whole grams, matching a {@code weight_grams INTEGER} column.
 *
 * <p>Construction is exact: a value in kilos that is not a whole number of grams is rejected.
 */
public final class Weight {

    private static final int GRAMS_PER_KILO_EXPONENT = 3;

    private final int grams;

    private Weight(int grams) {
        this.grams = grams;
    }

    public static Weight ofGrams(int grams) {
        if (grams <= 0) {
            throw new InvalidWeightException("Weight must be positive: " + grams + "g");
        }
        return new Weight(grams);
    }

    public static Weight ofKilos(BigDecimal kilos) {
        if (kilos == null) {
            throw new InvalidWeightException("Kilos must not be null");
        }
        if (kilos.signum() <= 0) {
            throw new InvalidWeightException("Weight must be positive: " + kilos + "kg");
        }
        try {
            return ofGrams(kilos.movePointRight(GRAMS_PER_KILO_EXPONENT)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .intValueExact());
        } catch (ArithmeticException exception) {
            throw new InvalidWeightException("Weight is not a whole number of grams: " + kilos + "kg");
        }
    }

    public int grams() {
        return grams;
    }

    /** Weight in kilos with scale 3: {@code 0.437} for 437 grams. */
    public BigDecimal kilos() {
        return BigDecimal.valueOf(grams, GRAMS_PER_KILO_EXPONENT);
    }

    /** Price of this weight at the given price per kilo, rounded HALF_UP to two decimal places. */
    public Money priceAt(Money pricePerKilo) {
        if (pricePerKilo == null) {
            throw InvalidMoneyException.invalid("Price per kilo must not be null");
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
