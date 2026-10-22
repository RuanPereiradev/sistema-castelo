package br.com.castel.restaurant.domain;

/**
 * Where a payment sent from the phone of a table stands (decision #16 of task qr-table-payment).
 *
 * <p>Born {@code PENDING}, holding the items it pays. It leaves {@code PENDING} once: to
 * {@code CONFIRMED} when the waiter saw the receipt, to {@code REJECTED} when the receipt does not
 * match, to {@code EXPIRED} when nobody answered in 30 minutes. A confirmed request that is refunded
 * stays {@code CONFIRMED}, with the refund recorded on it (decision #30).
 */
public enum TablePaymentRequestStatus {

    PENDING,
    CONFIRMED,
    REJECTED,
    EXPIRED;

    /** Only a {@code PENDING} request is confirmed, rejected or expired. */
    public boolean isPending() {
        return this == PENDING;
    }

    /** Stored as expired by the job. */
    public boolean isExpired() {
        return this == EXPIRED;
    }

    /** Only a {@code CONFIRMED} request put money on the folio, and only it is refunded. */
    public boolean isConfirmed() {
        return this == CONFIRMED;
    }

    /** Every status but {@code PENDING} is final. */
    public boolean isResolved() {
        return !isPending();
    }
}
