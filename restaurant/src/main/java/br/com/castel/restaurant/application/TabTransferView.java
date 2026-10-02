package br.com.castel.restaurant.application;

import br.com.castel.restaurant.domain.Tab;
import java.util.Objects;

/**
 * The two tabs of a transfer, as they stand right after it: the screen redraws both, since money
 * left one and arrived on the other.
 */
public record TabTransferView(Tab source, Tab destination, int movedItems) {

    public TabTransferView {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
    }
}
