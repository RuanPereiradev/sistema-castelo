package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * A request from the QR code of a table was sent, confirmed, rejected or expired. {@code status} is
 * the status it has now: {@code PENDING} means it was just sent.
 *
 * <p>Carries only ids: the floor is told that something changed and reloads its list (decision #25).
 */
public record TableOrderRequestStatusChanged(TableOrderRequestId requestId, DiningTableId diningTableId,
        TableOrderRequestStatus status, Instant occurredAt) implements DomainEvent {

    public TableOrderRequestStatusChanged {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(diningTableId, "diningTableId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** The request as it stands, at the moment of the change. */
    public static TableOrderRequestStatusChanged of(TableOrderRequest request, Instant occurredAt) {
        return new TableOrderRequestStatusChanged(request.id(), request.diningTableId(), request.status(), occurredAt);
    }
}
