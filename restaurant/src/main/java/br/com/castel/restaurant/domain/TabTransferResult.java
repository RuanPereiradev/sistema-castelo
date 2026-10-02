package br.com.castel.restaurant.domain;

import java.util.List;
import java.util.Objects;

/**
 * What a move of items leaves behind: the rows of the trail to store and the events to publish, one
 * of each per item moved and in the same order.
 *
 * <p>Two lists instead of one because they serve different readers. The trail is a record, kept for
 * the audit; the event carries only what the kitchen display needs, and never the author.
 */
public record TabTransferResult(List<TabItemTransfer> transfers, List<TabItemTransferred> events) {

    public TabTransferResult {
        transfers = List.copyOf(Objects.requireNonNull(transfers, "transfers"));
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }

    /** How many items moved. */
    public int movedItems() {
        return transfers.size();
    }
}
