package br.com.castel.restaurant.domain;

import java.util.Optional;

/**
 * Where the {@link PixReceiver} of the property is kept: the {@code setting} table, under
 * {@code pix.key}, {@code pix.receiver-name} and {@code pix.city} (decision #23 of task
 * qr-table-payment). Implemented in {@code restaurant.infra}.
 */
public interface PixReceiverRepository {

    /** The receiver, or empty while any of the three settings is missing. */
    Optional<PixReceiver> find();

    void save(PixReceiver receiver);
}
