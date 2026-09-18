package br.com.castel.app.web;

/**
 * One invalid field inside a 400 {@code VALIDATION_FAILED} body.
 *
 * @param field name of the field, in {@code camelCase}, exactly as it arrived in the JSON
 * @param code the broken rule in {@code UPPER_SNAKE_CASE} ({@code NOT_BLANK}, {@code SIZE}), never
 *        the validator's default English message, which the front end cannot translate
 */
public record InvalidField(String field, String code) {
}
