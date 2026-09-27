package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.Money;
import java.util.Objects;

/**
 * The confirmed {@code CASH} payments linked to a cash drawer session, as the database sums them. A
 * refunded payment is not among them. Read by the use case and handed to the session, which cannot
 * see the payments of the folios.
 */
public record CashPaymentTotals(Money total, long count) {

    private static final CashPaymentTotals NONE = new CashPaymentTotals(Money.ZERO, 0);

    public CashPaymentTotals {
        Objects.requireNonNull(total, "total");
        if (count < 0) {
            throw new IllegalStateException("A count of payments is never negative");
        }
    }

    /** No cash payment at all. */
    public static CashPaymentTotals none() {
        return NONE;
    }
}
