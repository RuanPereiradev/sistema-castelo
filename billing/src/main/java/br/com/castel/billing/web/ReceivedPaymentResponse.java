package br.com.castel.billing.web;

import br.com.castel.billing.application.ReceivedPayment;
import br.com.castel.billing.domain.Payment;
import java.time.Instant;

/**
 * Answer to a payment: the payment and the balance of the folio after it. A retry answers the same
 * payment, with the balance read again.
 */
public record ReceivedPaymentResponse(
        String id, String method, String amount, String status, Instant paidAt, String balance) {

    public static ReceivedPaymentResponse from(ReceivedPayment received) {
        Payment payment = received.payment();
        return new ReceivedPaymentResponse(
                payment.id().value().toString(),
                payment.method().name(),
                payment.amount().asString(),
                payment.status().name(),
                payment.paidAt(),
                received.balance().asString());
    }
}
