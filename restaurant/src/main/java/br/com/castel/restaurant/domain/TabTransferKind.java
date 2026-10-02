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

    /**
     * Whether the tab the items arrive on is the same party that left, rather than a second group
     * (decision T18).
     *
     * <p>Only a table move is: the tab on the destination table is an artefact of how the move is
     * built, so what the operator chose on the tab being absorbed carries over — the split of each
     * line, the number of guests, the service charge they turned off. A transfer and a merge put
     * items on a tab that has a party of its own, whose own choices rule.
     */
    public boolean carriesTheSameParty() {
        return this == MOVE;
    }
}
