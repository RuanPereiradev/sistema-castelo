package br.com.castel.payment.api;

/** Where an attempt to charge someone stands at the acquirer. */
public enum PaymentStatus {

    /** Created at the acquirer, waiting for the payer. */
    PENDING,

    PAID,

    /** The acquirer refused it; another attempt may still succeed. */
    FAILED,

    /** Given up before being paid, by the operator or by the payer. */
    CANCELLED,

    /** The window to pay it closed. */
    EXPIRED;

    public boolean isSettled() {
        return this == PAID;
    }

    /** Whether the acquirer may still change this status. */
    public boolean isPending() {
        return this == PENDING;
    }
}
