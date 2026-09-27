package br.com.castel.billing.application;

import br.com.castel.billing.domain.CashDrawerSession;
import br.com.castel.billing.domain.CashPaymentTotals;
import java.util.Objects;

/**
 * A cash drawer session and the confirmed cash payments linked to it, read in the same transaction.
 * The session cannot see the payments, which belong to the folios; what reads it needs both.
 */
public record CashDrawerSessionWithTotals(CashDrawerSession session, CashPaymentTotals cashPayments) {

    public CashDrawerSessionWithTotals {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(cashPayments, "cashPayments");
    }
}
