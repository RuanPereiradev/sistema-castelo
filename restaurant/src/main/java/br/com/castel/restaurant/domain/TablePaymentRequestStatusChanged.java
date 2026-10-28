package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * A payment from the phone of a table was sent, confirmed, rejected, expired or refunded.
 * {@code status} is the status it has now: {@code PENDING} means it was just sent, and a
 * {@code CONFIRMED} one with {@code refunded} was just refunded.
 *
 * <p>Carries only ids: the floor is told that something changed and reloads its list.
 */
public record TablePaymentRequestStatusChanged(TablePaymentRequestId requestId, DiningTableId diningTableId,
        TablePaymentRequestStatus status, boolean refunded, Instant occurredAt) implements DomainEvent {

    public TablePaymentRequestStatusChanged {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(diningTableId, "diningTableId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** The request as it stands, at the moment of the change. */
    public static TablePaymentRequestStatusChanged of(TablePaymentRequest request, Instant occurredAt) {
        return new TablePaymentRequestStatusChanged(
                request.id(), request.diningTableId(), request.status(), request.isRefunded(), occurredAt);
    }
}
