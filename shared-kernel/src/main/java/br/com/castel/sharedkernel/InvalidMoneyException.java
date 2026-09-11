package br.com.castel.sharedkernel;

import java.io.Serial;

/**
 * Thrown when a {@link Money} cannot be built or operated on.
 *
 * <p>Carries one of two codes: {@link #INVALID_MONEY} for null or unparseable input, and
 * {@link #MONEY_SCALE_EXCEEDED} when the amount would need rounding to fit two decimal places.
 */
public final class InvalidMoneyException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String INVALID_MONEY = "INVALID_MONEY";
    public static final String MONEY_SCALE_EXCEEDED = "MONEY_SCALE_EXCEEDED";

    private InvalidMoneyException(String code, String message) {
        super(code, message);
    }

    static InvalidMoneyException invalid(String message) {
        return new InvalidMoneyException(INVALID_MONEY, message);
    }

    static InvalidMoneyException scaleExceeded(Object amount) {
        return new InvalidMoneyException(
                MONEY_SCALE_EXCEEDED, "Amount has more than two decimal places: " + amount);
    }
}
