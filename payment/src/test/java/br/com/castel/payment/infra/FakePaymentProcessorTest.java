package br.com.castel.payment.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import br.com.castel.payment.api.PaymentIntent;
import br.com.castel.payment.api.PaymentIntentId;
import br.com.castel.payment.api.PaymentRequest;
import br.com.castel.payment.api.PaymentStatus;
import br.com.castel.sharedkernel.Money;
import java.net.URI;
import org.junit.jupiter.api.Test;

class FakePaymentProcessorTest {

    private final FakePaymentProcessor processor = new FakePaymentProcessor();

    @Test
    void shouldCreatePendingIntentWithAmountAndInvalidDomainPayerUrl() {
        PaymentIntent intent = processor.createPayment(requestOf("180.00"));

        assertThat(intent.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(intent.amount()).isEqualTo(Money.of("180.00"));
        assertThat(intent.payerUrl())
                .contains(URI.create("https://payment.invalid/intents/" + intent.intentId().value()));
    }

    @Test
    void shouldSettleAsPaidWhenCentsAreNotOne() {
        PaymentIntent intent = processor.createPayment(requestOf("10.00"));

        assertThat(processor.statusOf(intent.intentId())).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void shouldFailWhenCentsAreOne() {
        PaymentIntent failing = processor.createPayment(requestOf("10.01"));
        PaymentIntent paid = processor.createPayment(requestOf("10.11"));

        assertThat(processor.statusOf(failing.intentId())).isEqualTo(PaymentStatus.FAILED);
        assertThat(processor.statusOf(paid.intentId())).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void shouldReadTheCentsOfANegativeAmountToo() {
        PaymentIntent negative = processor.createPayment(requestOf("-10.01"));

        assertThat(processor.statusOf(negative.intentId())).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldCreateDistinctIntentsForIdenticalRequests() {
        PaymentIntent first = processor.createPayment(requestOf("50.00"));
        PaymentIntent second = processor.createPayment(requestOf("50.00"));

        assertThat(first.intentId()).isNotEqualTo(second.intentId());
    }

    @Test
    void shouldRejectStatusOfIntentItNeverCreated() {
        UnknownPaymentIntentException failure = catchThrowableOfType(
                UnknownPaymentIntentException.class, () -> processor.statusOf(PaymentIntentId.newId()));

        assertThat(failure.code()).isEqualTo("PAYMENT_INTENT_NOT_FOUND");
    }

    private static PaymentRequest requestOf(String amount) {
        return new PaymentRequest(Money.of(amount), "Stay", "FOLIO-1");
    }
}
