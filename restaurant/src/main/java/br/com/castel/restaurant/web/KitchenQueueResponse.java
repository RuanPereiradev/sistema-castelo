package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.KitchenQueueView;
import java.util.List;

/** The queue of one station, with the moment of the server and its two delay limits in minutes. */
public record KitchenQueueResponse(
        String station,
        String serverTime,
        int warningAfterMinutes,
        int lateAfterMinutes,
        List<KitchenTicketResponse> items) {

    public static KitchenQueueResponse from(KitchenQueueView queue) {
        return new KitchenQueueResponse(
                queue.station().name(),
                queue.serverTime().toString(),
                queue.warningAfterMinutes(),
                queue.lateAfterMinutes(),
                queue.tickets().stream().map(KitchenTicketResponse::from).toList());
    }
}
