package br.com.castel.sharedkernel;

/**
 * Whole number of units, from 1 to 999.
 *
 * <p>The upper bound catches typing mistakes; it also applies to the result of {@link #plus(Quantity)}.
 */
public final class Quantity {

    private static final int MINIMUM = 1;
    private static final int MAXIMUM = 999;

    private final int value;

    private Quantity(int value) {
        this.value = value;
    }

    public static Quantity of(int value) {
        if (value < MINIMUM || value > MAXIMUM) {
            throw new InvalidQuantityException(
                    "Quantity must be between " + MINIMUM + " and " + MAXIMUM + ": " + value);
        }
        return new Quantity(value);
    }

    public int value() {
        return value;
    }

    public Quantity plus(Quantity other) {
        if (other == null) {
            throw new InvalidQuantityException("Quantity must not be null");
        }
        return of(value + other.value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Quantity quantity && value == quantity.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
