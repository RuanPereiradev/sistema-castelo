package br.com.castel.restaurant.domain;

/**
 * Where a tab stands.
 *
 * <p>Task 2.2 moves a tab only from {@code OPEN} to {@code CANCELLED}. {@code CLOSING} and
 * {@code CLOSED} arrive with the closing of task 3.2 and {@code MERGED} with task 3.6; their answers
 * here are already the ones those tasks rely on.
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
}
