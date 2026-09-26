package br.com.castel.billing.domain;

/**
 * Where a payment stands. A manual payment is born {@link #CONFIRMED}; {@link #PENDING} and
 * {@link #FAILED} belong to the online payment of version 1.1.
 */
public enum PaymentStatus {

    PENDING,
    CONFIRMED,
    FAILED,

    /** Undone by an {@code ADMIN} (decision #2 of task 1.3); the row stays, the balance goes back up. */
    REFUNDED;

    /** Only a confirmed payment takes from the balance. */
    public boolean countsTowardsBalance() {
        return this == CONFIRMED;
    }

    /** Only a confirmed payment can be refunded. */
    public boolean acceptsRefund() {
        return this == CONFIRMED;
    }
}
