package br.com.castel.billing.domain;

/** Where a cash drawer session stands. A session never reopens (task 2.4). */
public enum CashDrawerSessionStatus {

    OPEN,
    CLOSED;

    /** Only an open session takes a drop, a supply or its closing. */
    public boolean acceptsMovements() {
        return this == OPEN;
    }
}
