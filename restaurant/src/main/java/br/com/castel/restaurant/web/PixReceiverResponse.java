package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.PixReceiver;
import java.util.Optional;

/** The Pix receiver as typed by the administrator; every field null while it is not configured. */
public record PixReceiverResponse(String key, String receiverName, String city) {

    static PixReceiverResponse from(Optional<PixReceiver> receiver) {
        return new PixReceiverResponse(
                receiver.map(PixReceiver::key).orElse(null),
                receiver.map(PixReceiver::receiverName).orElse(null),
                receiver.map(PixReceiver::city).orElse(null));
    }
}
