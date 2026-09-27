package br.com.castel.billing.api;

import br.com.castel.sharedkernel.EntityId;
import java.util.UUID;

/**
 * Identity of a payment received against a folio.
 *
 * <p>Born in {@code domain} (decision #20 of task 1.3) and moved to {@code api} when the closing
 * of a tab (task 3.2) started receiving payments through {@link FolioFacade}.
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
