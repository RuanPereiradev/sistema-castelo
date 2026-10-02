package br.com.castel.restaurant.domain;

/**
 * Which operation moved an item from one tab to another (decision T15).
 *
 * <p>Recorded on every row of {@code tab_item_transfer}. Without it the trail would say that the
 * item went from tab A to tab B but not whether a waiter moved one beer, a whole tab was absorbed,
 * or the group changed table — and a table move of eight items would read exactly like eight
 * separate transfers made one by one.
 *
 * <p>The caller never informs it: each operation of {@link Tab} knows which one it is.
 */
public enum TabTransferKind {

    /** One or more whole lines moved by hand, both tabs staying open. */
    TRANSFER,

    /** Every active item of a tab that was absorbed, which went to {@code MERGED}. */
    MERGE,

    /** Every active item of a tab that changed table, which is a merge into a brand new tab. */
    MOVE;

    /** Whether the source tab is left behind as {@code MERGED} instead of staying open. */
    public boolean emptiesTheSource() {
        return this != TRANSFER;
    }
}
