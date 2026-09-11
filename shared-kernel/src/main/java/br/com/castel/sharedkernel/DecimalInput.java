package br.com.castel.sharedkernel;

import java.math.BigDecimal;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Validation shared by every value object built from a decimal number.
 *
 * <p>{@link BigDecimal#scale()} and {@link BigDecimal#precision()} are cheap, but materializing a
 * number with an extreme scale or precision ({@code toPlainString}, {@code setScale},
 * {@code stripTrailingZeros}, {@code movePointLeft}/{@code movePointRight} or arithmetic) can exhaust
 * CPU or memory. {@link #requireBounded(BigDecimal, Function)} must therefore run before any of those.
 *
 * <p>Each caller supplies the factory of its own exception, so the rejection carries the caller's
 * code. Messages never include the rejected value.
 */
final class DecimalInput {

    static final int MAXIMUM_SCALE_MAGNITUDE = 100;
    static final int MAXIMUM_PRECISION = 100;
    static final int MAXIMUM_TEXT_LENGTH = 25;

    private static final Pattern PLAIN_DECIMAL = Pattern.compile("[+-]?[0-9]+(\\.[0-9]+)?");

    private DecimalInput() {
    }

    /**
     * Rejects a decimal whose scale lies outside {@code -100..100} or whose precision exceeds 100 digits.
     *
     * @param value a non-null decimal
     * @return the same value, when within bounds
     */
    static BigDecimal requireBounded(BigDecimal value, Function<String, ? extends DomainException> rejection) {
        if (Math.abs((long) value.scale()) > MAXIMUM_SCALE_MAGNITUDE || value.precision() > MAXIMUM_PRECISION) {
            throw rejection.apply("Decimal value exceeds scale magnitude " + MAXIMUM_SCALE_MAGNITUDE
                    + " or precision " + MAXIMUM_PRECISION);
        }
        return value;
    }

    /**
     * Parses text as a plain decimal, checking in this order: surrounding whitespace is stripped, the
     * stripped text must have at most 25 characters, and it must be an optional sign ({@code +} or
     * {@code -}), ASCII digits and an optional fraction with at least one digit.
     *
     * @param text non-null text
     */
    static BigDecimal parsePlainDecimal(String text, Function<String, ? extends DomainException> rejection) {
        String stripped = text.strip();
        if (stripped.length() > MAXIMUM_TEXT_LENGTH) {
            throw rejection.apply("Decimal text exceeds " + MAXIMUM_TEXT_LENGTH + " characters");
        }
        if (!PLAIN_DECIMAL.matcher(stripped).matches()) {
            throw rejection.apply("Decimal text is not a plain decimal number");
        }
        return new BigDecimal(stripped);
    }
}
