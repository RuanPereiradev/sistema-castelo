package br.com.castel.billing.api;

import br.com.castel.sharedkernel.Money;
import java.time.Instant;

/**
 * One line of a folio, as the front desk reads it at check-out.
 *
 * <p>A reversal is a line of its own, with a negative {@code amount} and {@code reversalOf} naming
 * the charge it undoes. Nothing is ever removed from a folio (decision #5).
 */
public record ChargeView(
        ChargeId chargeId, Money amount, String description, Instant postedAt, ChargeId reversalOf) {

    /** Whether this line undoes another one. */
    public boolean isReversal() {
        return reversalOf != null;
    }
}
