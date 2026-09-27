package br.com.castel.restaurant.domain;

/**
 * Where a tab stands.
 *
 * <p>Task 2.2 moves a tab from {@code OPEN} to {@code CANCELLED}. Task 3.2 moves it from {@code OPEN}
 * to {@code CLOSING} (the pre-bill, with the total posted on the folio), back to {@code OPEN} by the
 * waiter's override, and from {@code CLOSING} to {@code CLOSED} once the folio is settled.
 * {@code MERGED} arrives with task 3.6; its answers here are already the ones that task relies on.
 */
public enum TabStatus {

    OPEN(true, true),
    CLOSING(false, true),
    CLOSED(false, false),
    CANCELLED(false, false),
    MERGED(false, false);

    private final boolean open;
    private final boolean holdsItsPlace;

    TabStatus(boolean open, boolean holdsItsPlace) {
        this.open = open;
        this.holdsItsPlace = holdsItsPlace;
    }

    /** Only an {@code OPEN} tab takes new items. */
    public boolean acceptsItems() {
        return open;
    }

    /** Only an {@code OPEN} tab has items cancelled: once closing starts, the bill is being settled. */
    public boolean acceptsItemCancellation() {
        return open;
    }

    /** Only an {@code OPEN} tab is cancelled as a whole (decision #10). */
    public boolean acceptsCancellation() {
        return open;
    }

    /**
     * Whether the tab still occupies its dining table or card: {@code OPEN} and {@code CLOSING}, the
     * same two statuses the partial unique indexes count.
     */
    public boolean holdsItsPlace() {
        return holdsItsPlace;
    }

    // ------------------------------------------------------------------ closing (task 3.2)

    /** Only an {@code OPEN} tab has its service charge turned off or back on. */
    public boolean acceptsServiceChargeChange() {
        return this == OPEN;
    }

    /** Only an {@code OPEN} tab starts closing: its total is frozen and posted on the folio. */
    public boolean acceptsClosing() {
        return this == OPEN;
    }

    /** Only a {@code CLOSING} tab is reopened, by the waiter's override that reverses its charge. */
    public boolean acceptsReopening() {
        return this == CLOSING;
    }

    /**
     * A tab takes payments once its total is on the folio: {@code CLOSING}, and {@code CLOSED} too, so
     * that a retry of a payment already registered answers the original instead of a false error —
     * the folio refuses anything new once it is closed.
     */
    public boolean acceptsPayment() {
        return this == CLOSING || this == CLOSED;
    }

    /** Only a {@code CLOSING} tab is closed for good, with its folio settled. */
    public boolean acceptsSettlement() {
        return this == CLOSING;
    }

    /**
     * The split of the bill — the group of each item and the number of guests — changes while the
     * bill is still being settled: {@code OPEN} and {@code CLOSING}. It never changes the total.
     */
    public boolean acceptsSplitChange() {
        return this == OPEN || this == CLOSING;
    }
}
