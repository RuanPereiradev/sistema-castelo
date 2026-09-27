package br.com.castel.restaurant.domain;

/**
 * Where a closed tab was settled: paid right there, or posted on the account of a guest's room.
 *
 * <p>Task 3.2 only writes {@code DIRECT_PAYMENT}; {@code ROOM_ACCOUNT} arrives with task 3.3.
 */
public enum TabDestination {
    DIRECT_PAYMENT,
    ROOM_ACCOUNT
}
