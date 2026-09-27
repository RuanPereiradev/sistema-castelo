package br.com.castel.restaurant.domain;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.billing.api.ReceivedPaymentView;
import br.com.castel.sharedkernel.Money;

/**
 * What the {@link Tab} asks of the billing module while it closes: the port the aggregate calls, so
 * the decisions that are the tab's own — open a folio or reuse the one it has, which charge is in
 * force — stay in the aggregate, and a test runs them without a database.
 *
 * <p>Implemented in {@code restaurant.infra} over the {@code FolioFacade}, in the transaction of the
 * caller: the tab and its folio change together or not at all (decision #11 of task 1.3). The rules
 * of the folio itself answer from here with billing's own codes, which this port never translates.
 */
public interface TabBilling {

    /** Opens the {@code TAB} folio of the tab. */
    FolioId openFolio(TabId tabId);

    /**
     * Posts the total of the tab on its folio, as one {@code TabCharge} (decision F6), with the tab as
     * the source of the posting.
     */
    ChargeId charge(FolioId folioId, TabId tabId, Money amount, String description);

    /** Reverses the charge in force, with the reason of the reopening (decision F10). */
    void reverse(FolioId folioId, ChargeId chargeId, String reason);

    /**
     * Registers money received against the folio, by the rules of the counter: idempotency key,
     * manual method, amount above zero, never above the balance, and the cash drawer of task 2.4.
     */
    ReceivedPaymentView receivePayment(FolioId folioId, PaymentMethod method, Money amount, String idempotencyKey);

    /** What the folio still owes. */
    Money balanceOf(FolioId folioId);

    /** What was paid on the folio: the sum of its charges minus its balance. */
    Money paidOn(FolioId folioId);

    /**
     * Closes the folio.
     *
     * <p>Refuses a balance other than exactly zero with billing's {@code FOLIO_BALANCE_NOT_ZERO}: the
     * tab relies on this refusal to never close, or be cancelled, with money pending (decision D4).
     */
    void closeFolio(FolioId folioId);
}
