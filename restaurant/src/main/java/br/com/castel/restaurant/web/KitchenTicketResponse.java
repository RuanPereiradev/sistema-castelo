package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.KitchenTicket;
import java.time.Instant;
import java.util.List;

/**
 * A {@link KitchenTicket} as the screens read it, the same shape on the queue, in the answer of a
 * transition and in a STOMP message. Moments in ISO-8601 UTC; what does not apply is null.
 */
public record KitchenTicketResponse(
        String itemId,
        String tabId,
        String origin,
        String diningTableLabel,
        Integer cardNumber,
        String itemName,
        String variantName,
        int quantity,
        List<ModifierResponse> modifiers,
        String specialInstructions,
        String prepStation,
        String status,
        String orderedAt,
        String preparationStartedAt,
        String readyAt,
        String updatedAt) {

    public static KitchenTicketResponse from(KitchenTicket ticket) {
        return new KitchenTicketResponse(
                ticket.itemId().value().toString(),
                ticket.tabId().value().toString(),
                ticket.origin().name(),
                ticket.diningTableLabel(),
                ticket.cardNumber(),
                ticket.itemName(),
                ticket.variantName(),
                ticket.quantity(),
                ticket.modifiers().stream().map(ModifierResponse::from).toList(),
                ticket.specialInstructions(),
                ticket.prepStation().name(),
                ticket.status().name(),
                ticket.orderedAt().toString(),
                textOf(ticket.preparationStartedAt()),
                textOf(ticket.readyAt()),
                ticket.updatedAt().toString());
    }

    private static String textOf(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    /** A modifier by name, with how many go on each unit. No price: the kitchen needs none. */
    public record ModifierResponse(String name, int quantity) {

        static ModifierResponse from(KitchenTicket.ModifierLine line) {
            return new ModifierResponse(line.name(), line.quantity());
        }
    }
}
