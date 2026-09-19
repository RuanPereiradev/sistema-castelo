package br.com.castel.payment.api;

/**
 * The acquirer, as the system sees it.
 *
 * <p>A port with a fake adapter in version 1, so the whole system can be built and operated before
 * anyone decides which provider to sign with. When the decision comes, only the adapter is written.
 */
public interface PaymentProcessor {

    /** Asks the acquirer to charge someone, and answers what it created. */
    PaymentIntent createPayment(PaymentRequest request);

    /** Where an attempt stands now, asked of the acquirer rather than of our own records. */
    PaymentStatus statusOf(PaymentIntentId intentId);
}
