package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.EntityId;
import java.util.UUID;

/**
 * Identity of a {@link Payment}.
 *
 * <p>Lives in {@code domain} and not in {@code api} (decision #20 of task 1.3): no other module
 * refers to a payment yet.
 */
public record PaymentId(UUID value) implements EntityId {

    public PaymentId {
        EntityId.requireValid(value);
    }

    public static PaymentId newId() {
        return EntityId.newId(PaymentId::new);
    }

    public static PaymentId of(String value) {
        return EntityId.of(value, PaymentId::new);
    }
}
