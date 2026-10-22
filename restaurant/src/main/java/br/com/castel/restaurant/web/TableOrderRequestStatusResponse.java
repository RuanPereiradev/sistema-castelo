package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.TableOrderRequest;
import java.time.Instant;

/**
 * A request as the phone that sent it reads it (decision #12): its status, {@code EXPIRED} from the
 * end of its window on, and the window itself. Nothing about who answered it.
 */
public record TableOrderRequestStatusResponse(String id, String status, String requestedAt, String expiresAt) {

    public static TableOrderRequestStatusResponse from(TableOrderRequest request, Instant now) {
        return new TableOrderRequestStatusResponse(
                request.id().value().toString(),
                request.statusAt(now).name(),
                request.requestedAt().toString(),
                request.expiresAt().toString());
    }
}
