package br.com.castel.app.web;

import java.util.regex.Pattern;

/**
 * Turns the name of a Bean Validation constraint into the stable code the front end translates:
 * {@code NotBlank} becomes {@code NOT_BLANK}, {@code Size} becomes {@code SIZE}.
 *
 * <p>The alternative would be shipping the validator's default message, which arrives in English
 * and changes with the validator's version.
 */
final class ValidationRuleCode {

    private static final Pattern WORD_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    /** Code used when the constraint that failed cannot be identified. */
    static final String UNKNOWN_RULE_CODE = "INVALID";

    private ValidationRuleCode() {
    }

    static String of(String constraintName) {
        if (constraintName == null || constraintName.isBlank()) {
            return UNKNOWN_RULE_CODE;
        }
        return WORD_BOUNDARY.matcher(constraintName).replaceAll("_").toUpperCase(java.util.Locale.ROOT);
    }
}
