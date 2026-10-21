package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.TableOrderRequest;
import br.com.castel.restaurant.domain.TableOrderRequestLine;
import br.com.castel.restaurant.domain.TableOrderRequestLineModifier;
import java.time.Instant;
import java.util.List;

/**
 * A request as the floor reads it: the table by its label and every line with the names of its item,
 * variant and modifiers as the menu has them now. A name is null when the menu no longer knows the
 * id, which the confirmation then refuses with its own code. {@code status} is {@code EXPIRED} from
 * the end of the window on; {@code tabId} is set only on {@code CONFIRMED}.
 */
public record TableOrderRequestResponse(
        String id,
        String status,
        String diningTableId,
        String diningTableLabel,
        String requestedAt,
        String expiresAt,
        String resolvedAt,
        String tabId,
        List<Line> lines) {

    /** Where the names of the lines are read from: the menu as it is now. */
    interface MenuNames {

        String diningTableLabel(TableOrderRequest request);

        String menuItemName(TableOrderRequestLine line);

        String variantName(TableOrderRequestLine line);

        String modifierName(TableOrderRequestLineModifier modifier);
    }

    public record Line(
            String menuItemId,
            String menuItemName,
            String variantId,
            String variantName,
            Integer quantity,
            List<Modifier> modifiers,
            String specialInstructions) {
    }

    public record Modifier(String modifierId, String modifierName, int quantity) {
    }

    static TableOrderRequestResponse from(TableOrderRequest request, MenuNames names, Instant now) {
        return new TableOrderRequestResponse(
                request.id().value().toString(),
                request.statusAt(now).name(),
                request.diningTableId().value().toString(),
                names.diningTableLabel(request),
                request.requestedAt().toString(),
                request.expiresAt().toString(),
                request.resolvedAt().map(Instant::toString).orElse(null),
                request.tabId().map(id -> id.value().toString()).orElse(null),
                request.lines().stream().map(line -> lineFrom(line, names)).toList());
    }

    private static Line lineFrom(TableOrderRequestLine line, MenuNames names) {
        return new Line(
                line.menuItemId().value().toString(),
                names.menuItemName(line),
                line.variantId().map(id -> id.value().toString()).orElse(null),
                names.variantName(line),
                line.quantity().orElse(null),
                line.modifiers().stream()
                        .map(modifier -> new Modifier(
                                modifier.modifierId().value().toString(),
                                names.modifierName(modifier),
                                modifier.quantity()))
                        .toList(),
                line.specialInstructions().orElse(null));
    }
}
