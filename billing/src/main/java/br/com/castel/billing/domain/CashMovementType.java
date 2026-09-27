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
    CASH_SUPPLY;

    /** The amount as it moves the expected amount of the drawer: a drop takes, a supply adds. */
    public Money signedAmount(Money amount) {
        return this == CASH_DROP ? amount.negate() : amount;
    }
}
