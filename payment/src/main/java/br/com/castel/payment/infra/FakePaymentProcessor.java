package br.com.castel.payment.infra;

import br.com.castel.payment.api.PaymentIntent;
import br.com.castel.payment.api.PaymentIntentId;
import br.com.castel.payment.api.PaymentProcessor;
import br.com.castel.payment.api.PaymentRequest;
import br.com.castel.payment.api.PaymentStatus;
import br.com.castel.sharedkernel.Money;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * An acquirer that charges nobody, active under {@code castel.payment.processor=fake}.
 *
 * <p>Every intent is created {@code PENDING}, with a payer URL on the reserved {@code .invalid}
 * domain (RFC 2606), and its outcome is decided at creation from the amount: cents {@code ,01} fail,
 * anything else is paid. {@link #statusOf} answers that outcome, with no clock and no transition over
 * time, so the same request always yields the same answer. State lives in memory and is lost on
 * restart.
 */
@Component
@ConditionalOnProperty(name = "castel.payment.processor", havingValue = "fake")
public class FakePaymentProcessor implements PaymentProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FakePaymentProcessor.class);

    private static final String PAYER_URL_PREFIX = "https://payment.invalid/intents/";
    private static final BigDecimal FAILING_CENTS = new BigDecimal("0.01");

    private final Map<PaymentIntentId, PaymentStatus> outcomes = new ConcurrentHashMap<>();

    public FakePaymentProcessor() {
        LOGGER.warn("Fake PaymentProcessor active: no payment reaches an acquirer");
    }

    @Override
    public PaymentIntent createPayment(PaymentRequest request) {
        Objects.requireNonNull(request, "request");
        PaymentIntentId intentId = PaymentIntentId.newId();
        outcomes.put(intentId, outcomeFor(request.amount()));
        URI payerUrl = URI.create(PAYER_URL_PREFIX + intentId.value());
        return new PaymentIntent(intentId, PaymentStatus.PENDING, request.amount(), Optional.of(payerUrl));
    }

    @Override
    public PaymentStatus statusOf(PaymentIntentId intentId) {
        Objects.requireNonNull(intentId, "intentId");
        PaymentStatus outcome = outcomes.get(intentId);
        if (outcome == null) {
            throw new UnknownPaymentIntentException(intentId);
        }
        return outcome;
    }

    private static PaymentStatus outcomeFor(Money amount) {
        BigDecimal cents = amount.amount().abs().remainder(BigDecimal.ONE);
        return cents.compareTo(FAILING_CENTS) == 0 ? PaymentStatus.FAILED : PaymentStatus.PAID;
    }
}
