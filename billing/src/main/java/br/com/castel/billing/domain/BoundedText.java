package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.util.function.Function;

/** The one rule every free text of the folio follows: trimmed, not blank, and within its column. */
final class BoundedText {

    private BoundedText() {
    }

    /**
     * @return the text trimmed
     * @throws DomainException built by {@code rejection} when the text is missing, blank or longer
     *     than {@code maximumLength} once trimmed
     */
    static String require(
            String text, int maximumLength, String what, Function<String, ? extends DomainException> rejection) {
        if (text == null || text.isBlank()) {
            throw rejection.apply("A " + what + " must not be blank");
        }
        String trimmed = text.trim();
        if (trimmed.length() > maximumLength) {
            throw rejection.apply("A " + what + " takes at most " + maximumLength + " characters");
        }
        return trimmed;
    }
}
