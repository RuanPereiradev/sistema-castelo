package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.FORTALEZA;
import static br.com.castel.restaurant.domain.TabFixtures.THURSDAY;
import static br.com.castel.restaurant.domain.TabFixtures.at;
import static br.com.castel.restaurant.domain.TabFixtures.steak;
import static br.com.castel.restaurant.domain.TabFixtures.wednesdayAt;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * {@code MenuItem.isWithinAvailabilityWindowAt}, new in task 2.2: answers only about the schedule, so
 * the tab can tell "ran out" ({@code MENU_ITEM_UNAVAILABLE}) from "not served now"
 * ({@code MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW}).
 */
class MenuItemAvailabilityWindowTest {

    private static MenuItem lunchSpecial() {
        MenuItem item = steak();
        item.serveBetween(DayOfWeek.WEDNESDAY, LocalTime.of(11, 0), LocalTime.of(15, 0));
        return item;
    }

    @Test
    void shouldBeWithinTheWindowAtAnyMomentWhenTheItemHasNoWindow() {
        MenuItem item = steak();

        assertThat(item.isWithinAvailabilityWindowAt(at(THURSDAY, "03:00"), FORTALEZA)).isTrue();
    }

    @Test
    void shouldBeWithinTheWindowInsideTheScheduleEvenWhenTheItemRanOut() {
        MenuItem item = lunchSpecial();
        item.markUnavailable();

        assertThat(item.isWithinAvailabilityWindowAt(wednesdayAt("12:00"), FORTALEZA)).isTrue();
    }

    @Test
    void shouldBeOutsideTheWindowOnADayTheItemIsNotServed() {
        MenuItem item = lunchSpecial();

        assertThat(item.isWithinAvailabilityWindowAt(at(THURSDAY, "12:00"), FORTALEZA)).isFalse();
    }
}
