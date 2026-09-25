package br.com.castel.restaurant.domain;

/**
 * A modifier chosen on an order, with how many of it go on each unit of the item (decision #11).
 *
 * <p>Carries the {@link Modifier} itself, not its id: the tab freezes its name and price at the
 * moment of the order. Whether the item offers it, whether it is active and whether the quantity fits
 * is checked by {@link Tab#addItem}.
 */
public record ModifierChoice(Modifier modifier, int quantity) {
}
