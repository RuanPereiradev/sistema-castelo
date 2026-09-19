package br.com.castel.restaurant.api;

/**
 * Where an item is prepared, which is how the kitchen display splits the queue.
 *
 * <p>Public because the KDS routes by it and the tab carries it on each item.
 */
public enum PrepStation {

    KITCHEN,
    PIZZA,
    BAR
}
