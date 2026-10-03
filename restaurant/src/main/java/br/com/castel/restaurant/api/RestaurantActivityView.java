package br.com.castel.restaurant.api;

import java.util.Optional;

/**
 * How much the floor moved in a period.
 *
 * <p><b>The number of people is partial, and this record says so instead of hiding it.</b> The
 * number of guests on a tab is optional: the waiter informs it when the bill is to be split, and
 * often does not. So {@link #guests()} counts only the tabs that carry it, and
 * {@link #tabsWithGuestCount()} says how many of the {@link #openedTabs()} those were. Whoever
 * reports has to show both — "412 people across 180 of 213 tabs" is honest; "412 people" is not.
 *
 * @param openedTabs tabs opened in the period, cancelled and merged ones left out
 * @param tabsWithGuestCount how many of them carry the number of guests
 * @param guests the sum of the guests informed
 * @param tableServiceTabs of the opened tabs, how many were on a dining table
 * @param selfServiceTabs of the opened tabs, how many were on a self-service card
 */
public record RestaurantActivityView(
        int openedTabs,
        int tabsWithGuestCount,
        int guests,
        int tableServiceTabs,
        int selfServiceTabs) {

    public static final RestaurantActivityView NONE = new RestaurantActivityView(0, 0, 0, 0, 0);

    /**
     * How much of the period's tabs the number of people covers, from 0 to 1; empty when no tab was
     * opened. Whoever shows the number of guests shows this next to it, or shows a number that
     * looks complete and is not.
     */
    public Optional<Double> guestCountCoverage() {
        return openedTabs == 0 ? Optional.empty() : Optional.of((double) tabsWithGuestCount / openedTabs);
    }

    /** Whether every tab of the period informed its number of guests, so the count is whole. */
    public boolean guestCountIsComplete() {
        return openedTabs > 0 && tabsWithGuestCount == openedTabs;
    }
}
