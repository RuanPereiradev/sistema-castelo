package br.com.castel.restaurant.web;

import java.time.Instant;

/**
 * What a STOMP topic of the kitchen display carries: what happened, when, and the ticket as it stands
 * after the commit. {@code cancellationReason} is set only on {@code CANCELLED}.
 */
public record KitchenDisplayMessage(
        String type,
        String occurredAt,
        KitchenTicketResponse item,
        String cancellationReason) {

    static final String ORDERED = "ORDERED";
    static final String STATUS_CHANGED = "STATUS_CHANGED";
    static final String CANCELLED = "CANCELLED";

    static KitchenDisplayMessage ordered(Instant occurredAt, KitchenTicketResponse item) {
        return new KitchenDisplayMessage(ORDERED, occurredAt.toString(), item, null);
    }

    static KitchenDisplayMessage statusChanged(Instant occurredAt, KitchenTicketResponse item) {
        return new KitchenDisplayMessage(STATUS_CHANGED, occurredAt.toString(), item, null);
    }

    static KitchenDisplayMessage cancelled(Instant occurredAt, KitchenTicketResponse item, String reason) {
        return new KitchenDisplayMessage(CANCELLED, occurredAt.toString(), item, reason);
    }
}
