package br.com.castel.restaurant.domain;

import java.util.Objects;

/**
 * What a table move leaves behind: the new tab, on the destination table, and the trail and events
 * of the items that came with it.
 *
 * <p>The new tab is not stored yet — the caller adds it, so the partial unique index of {@code tab}
 * is what answers that the table is already taken.
 */
public record TabMove(Tab newTab, TabTransferResult result) {

    public TabMove {
        Objects.requireNonNull(newTab, "newTab");
        Objects.requireNonNull(result, "result");
    }
}
