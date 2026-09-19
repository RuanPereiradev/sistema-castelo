package br.com.castel.payment.api;

import br.com.castel.sharedkernel.EntityId;
import java.util.UUID;

/** Identity of a {@code PaymentIntent}, the attempt to charge someone through an acquirer. */
public record PaymentIntentId(UUID value) implements EntityId {

    public PaymentIntentId {
        EntityId.requireValid(value);
    }

    public static PaymentIntentId newId() {
        return EntityId.newId(PaymentIntentId::new);
    }

    public static PaymentIntentId of(String value) {
        return EntityId.of(value, PaymentIntentId::new);
    }
}
