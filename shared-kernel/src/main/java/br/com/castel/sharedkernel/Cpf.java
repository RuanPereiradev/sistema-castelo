package br.com.castel.sharedkernel;

import java.util.regex.Pattern;

/**
 * Brazilian individual taxpayer number, stored as its eleven digits.
 *
 * <p>Input may contain dots, dashes and whitespace anywhere; any other non-digit character is
 * rejected. {@link #toString()} is not overridden, so the number does not leak into logs by accident.
 */
public final class Cpf {

    private static final int LENGTH = 11;
    private static final Pattern SEPARATORS = Pattern.compile("[.\\-\\s]");
    private static final Pattern ELEVEN_DIGITS = Pattern.compile("[0-9]{" + LENGTH + "}");

    private final String value;

    private Cpf(String value) {
        this.value = value;
    }

    public static Cpf of(String cpf) {
        if (cpf == null) {
            throw new InvalidCpfException("CPF must not be null");
        }
        String digits = SEPARATORS.matcher(cpf).replaceAll("");
        if (!ELEVEN_DIGITS.matcher(digits).matches()) {
            throw new InvalidCpfException("CPF must have exactly eleven digits and only . - or whitespace as separators");
        }
        if (hasAllDigitsEqual(digits)) {
            throw new InvalidCpfException("CPF must not be a repeated-digit sequence");
        }
        if (checkDigit(digits, 9) != digitAt(digits, 9) || checkDigit(digits, 10) != digitAt(digits, 10)) {
            throw new InvalidCpfException("CPF check digits do not match");
        }
        return new Cpf(digits);
    }

    /** The eleven digits, without separators. */
    public String value() {
        return value;
    }

    /** The number formatted as {@code 123.456.789-09}. */
    public String formatted() {
        return value.substring(0, 3) + "." + value.substring(3, 6) + "." + value.substring(6, 9)
                + "-" + value.substring(9);
    }

    private static boolean hasAllDigitsEqual(String digits) {
        return digits.chars().allMatch(character -> character == digits.charAt(0));
    }

    /** Computes the check digit at {@code position} (9 or 10) from the digits that precede it. */
    private static int checkDigit(String digits, int position) {
        int sum = 0;
        for (int index = 0; index < position; index++) {
            sum += digitAt(digits, index) * (position + 1 - index);
        }
        int remainder = (sum * 10) % LENGTH;
        return remainder == 10 ? 0 : remainder;
    }

    private static int digitAt(String digits, int index) {
        return digits.charAt(index) - '0';
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Cpf cpf && value.equals(cpf.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
