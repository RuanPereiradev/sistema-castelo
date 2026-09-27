package br.com.castel.billing.api;

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

    /** Whether the money lands in the cash drawer, and so in the open cash drawer session (task 2.4). */
    public boolean goesToCashDrawer() {
        return this == CASH;
    }
}
