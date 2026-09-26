package br.com.castel.billing.domain;

/** How a payment was made. */
public enum PaymentMethod {

    CASH,
    PIX,
    CREDIT_CARD,
    DEBIT_CARD,

    /** Consumption sent to the account of a room; never registered by hand (decision #6 of task 1.3). */
    ROOM_ACCOUNT;

    /** Whether an operator may register a payment by this method at the counter. */
    public boolean acceptsManualEntry() {
        return this != ROOM_ACCOUNT;
    }
}
