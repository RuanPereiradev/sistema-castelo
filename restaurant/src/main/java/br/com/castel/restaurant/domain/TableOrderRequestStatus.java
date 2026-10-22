package br.com.castel.restaurant.domain;

/**
 * Where an order sent from the QR code of a table stands (decision #18).
 *
 * <p>Born {@code PENDING}, waiting for a waiter. It leaves {@code PENDING} once and for good: to
 * {@code CONFIRMED} when a waiter puts it on the tab, to {@code REJECTED} when a waiter turns it
 * down, to {@code EXPIRED} when nobody answered in 30 minutes (decision #14).
 */
public enum TableOrderRequestStatus {

    PENDING,
    CONFIRMED,
    REJECTED,
    EXPIRED;

    /** Only a {@code PENDING} request is confirmed, rejected or expired. */
    public boolean isPending() {
        return this == PENDING;
    }

    /** Stored as expired by the job or by a submission on the same table (decision #22). */
    public boolean isExpired() {
        return this == EXPIRED;
    }

    /** Every status but {@code PENDING} is final. */
    public boolean isResolved() {
        return !isPending();
    }
}
