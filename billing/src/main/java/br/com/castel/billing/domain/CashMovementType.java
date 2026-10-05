package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.Money;

/**
 * What a cash movement does to the drawer. The opening float and the closing count are columns of
 * the session, not movements (decision C3 of task 2.4).
 */
public enum CashMovementType {

    /** Cash taken out of the drawer, to the safe or the bank. */
    CASH_DROP,

    /** Cash put into the drawer, for change. */
    CASH_SUPPLY,

    /**
     * Cash taken out of the drawer to pay an expense (decision D4 of task F1): the supplier at the
     * door, the delivery, whatever is settled from the till.
     *
     * <p>It is not a {@link #CASH_DROP}: a drop sends money to the safe and it is still the
     * business's; this leaves for good. The two are told apart so the summary can say what was
     * spent without counting what was merely moved.
     */
    EXPENSE_PAYMENT;

    /**
     * The amount as it moves the expected amount of the drawer: a supply adds, a drop and an
     * expense payment take. Without this the blind closing would report a shortfall for money that
     * left with the operator's knowledge, and a shortfall in the drawer looks like theft.
     */
    public Money signedAmount(Money amount) {
        return takesFromTheDrawer() ? amount.negate() : amount;
    }

    /** Whether the movement takes cash out of the drawer. */
    public boolean takesFromTheDrawer() {
        return this != CASH_SUPPLY;
    }

    /** Whether the cash left the business, rather than being moved to the safe. */
    public boolean leavesTheBusiness() {
        return this == EXPENSE_PAYMENT;
    }
}
