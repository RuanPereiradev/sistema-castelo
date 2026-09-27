package br.com.castel.restaurant.application;

import br.com.castel.restaurant.api.PrepStation;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The queue of one station as its screen opens it: the tickets, the moment of the server (so the
 * elapsed time does not depend on the clock of the monitor) and the two delay limits of the station
 * (K11), in minutes since the order. The screen colours; the backend only reads the limits.
 */
public record KitchenQueueView(
        PrepStation station,
        Instant serverTime,
        int warningAfterMinutes,
        int lateAfterMinutes,
        List<KitchenTicket> tickets) {

    public KitchenQueueView {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(serverTime, "serverTime");
        tickets = List.copyOf(tickets);
    }
}
