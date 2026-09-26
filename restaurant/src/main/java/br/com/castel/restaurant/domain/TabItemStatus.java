package br.com.castel.restaurant.domain;

/**
 * Where an item of the tab stands. Task 2.2 writes {@code PENDING}, {@code DELIVERED} (an item sold by
 * weight, decision #9) and {@code CANCELLED}; the kitchen display of task 3.5 walks the rest.
 */
public enum TabItemStatus {

    PENDING,
    IN_PREPARATION,
    READY,
    DELIVERED,
    CANCELLED;

    /** Counts in the subtotal and keeps the tab from being cancelled. */
    public boolean isActive() {
        return this != CANCELLED;
    }

    /** Anything not yet cancelled can be, delivered included (decision #6). */
    public boolean acceptsCancellation() {
        return this != CANCELLED;
    }
}
