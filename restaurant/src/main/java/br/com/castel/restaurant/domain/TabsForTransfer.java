package br.com.castel.restaurant.domain;

import java.util.Objects;

/**
 * The two tabs of one move, loaded and locked together in the order their ids sort, which is what
 * keeps two opposite moves from deadlocking.
 *
 * <p>A technical pair, not a concept of the restaurant: it exists because a repository method
 * returns two aggregates at once.
 */
public record TabsForTransfer(Tab source, Tab destination) {

    public TabsForTransfer {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
    }
}
