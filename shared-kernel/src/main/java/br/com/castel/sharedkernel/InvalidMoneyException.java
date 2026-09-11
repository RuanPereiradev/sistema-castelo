package br.com.castel.sharedkernel;

import java.io.Serial;

/**
 * Thrown when a {@link Money} cannot be built or operated on.
 *
 * <p>Carries one of three codes: {@link #INVALID_MONEY} for null, unparseable, too long or
 * scientific-notation input, a decimal with extreme scale or precision (and for a non-positive price
 * per kilo or a multiplication factor beyond the accepted scale or precision),
 * {@link #MONEY_SCALE_EXCEEDED} when the amount would need rounding to fit two decimal places, and
 * {@link #MONEY_OUT_OF_RANGE} when the amount does not fit a {@code NUMERIC(12,2)} column.
 *
 * <p>The message never includes the rejected value.
 */
public final class InvalidMoneyException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String INVALID_MONEY = "INVALID_MONEY";
    public static final String MONEY_SCALE_EXCEEDED = "MONEY_SCALE_EXCEEDED";
    public static final String MONEY_OUT_OF_RANGE = "MONEY_OUT_OF_RANGE";

    private InvalidMoneyException(String code, String message) {
        super(code, message);
    }

    static InvalidMoneyException invalid(String message) {
        return new InvalidMoneyException(INVALID_MONEY, message);
    }

    static InvalidMoneyException scaleExceeded() {
        return new InvalidMoneyException(MONEY_SCALE_EXCEEDED, "Amount has more than two decimal places");
    }

    static InvalidMoneyException outOfRange() {
        return new InvalidMoneyException(
                MONEY_OUT_OF_RANGE, "Amount is outside -9999999999.99 to 9999999999.99");
    }
}
