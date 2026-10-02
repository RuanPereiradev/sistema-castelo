package br.com.castel.restaurant.domain;

/**
 * Where an item of the tab stands. Task 2.2 writes {@code PENDING}, {@code DELIVERED} (an item sold by
 * weight, decision #9) and {@code CANCELLED}; the kitchen display of task 3.5 walks the rest.
 *
 * <p>The kitchen moves an item {@code PENDING → IN_PREPARATION → READY}, may skip straight to
 * {@code READY} (K2) and may undo one step (K3); the waiter delivers from any status still on the
 * queue (K4). {@code DELIVERED} never goes back: only a cancellation leaves it.
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

    // ---- kitchen display

    /** Shown on the kitchen display of its station until delivered or cancelled (K5). */
    public boolean isOnKitchenQueue() {
        return switch (this) {
            case PENDING, IN_PREPARATION, READY -> true;
            case DELIVERED, CANCELLED -> false;
        };
    }

    /** Only a pending item starts being prepared. */
    public boolean acceptsPreparationStart() {
        return this == PENDING;
    }

    /** Ready from preparation, or straight from pending for what needs no preparation (K2). */
    public boolean acceptsReady() {
        return switch (this) {
            case PENDING, IN_PREPARATION -> true;
            case READY, DELIVERED, CANCELLED -> false;
        };
    }

    /** The waiter delivers whatever is still on the queue, ready or not (K4). */
    public boolean acceptsDelivery() {
        return isOnKitchenQueue();
    }

    /** One step back from preparation or from ready (K3); pending and delivered do not go back. */
    public boolean acceptsUndo() {
        return switch (this) {
            case IN_PREPARATION, READY -> true;
            case PENDING, DELIVERED, CANCELLED -> false;
        };
    }

    /**
     * The status before the last tap (K3): preparation goes back to pending; ready goes back to
     * preparation when it was started, or to pending when ready skipped it (K2).
     *
     * @param preparationStarted whether the item carries the moment its preparation started
     * @throws IllegalStateException if this status does not {@link #acceptsUndo() accept undoing}
     */
    public TabItemStatus undoneTo(boolean preparationStarted) {
        return switch (this) {
            case READY -> preparationStarted ? IN_PREPARATION : PENDING;
            case IN_PREPARATION -> PENDING;
            case PENDING, DELIVERED, CANCELLED -> throw new IllegalStateException(this + " has no step to undo");
        };
    }

    /**
     * Whether an item in this status may carry the moment its preparation started: every status
     * past {@code PENDING}. Undoing back to {@code PENDING} erases it.
     */
    public boolean carriesPreparationStart() {
        return this != PENDING;
    }

    // ---- transfer and merge (task 3.6)

    /**
     * Anything not cancelled moves to another tab, a plate sold by weight included: it is born
     * {@code DELIVERED} and still belongs to whoever pays for it. A cancelled item stays on the tab
     * where it was cancelled, with its author and reason, because that is where the record of the
     * cancellation belongs.
     */
    public boolean acceptsTransfer() {
        return isActive();
    }
}
