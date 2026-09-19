package br.com.castel.restaurant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A stretch of the day in which an item is served: "pizza from 18:30 to 23:00".
 *
 * <p>A window whose {@code endTime} is before its {@code startTime} crosses midnight — 22:00 to
 * 02:00 is a valid window, and the item is served on both sides of the turn of the day.
 *
 * <p>{@code dayOfWeek} null means every day.
 *
 * <p>Part of the {@code MenuItem} aggregate: it has an id because the row needs one, but nothing
 * outside the aggregate loads or changes a window on its own. It does not carry the id of the item
 * either — the owning side writes {@code menu_item_id} through its join column, and mapping the
 * same column twice is what Hibernate refuses.
 */
@Entity
@Table(name = "availability_window")
public class AvailabilityWindow {

    @Id
    @Column(name = "id")
    private UUID id;

    /** ISO-8601 day number, 1 for Monday. Null means every day. */
    @Column(name = "day_of_week")
    private Short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected AvailabilityWindow() {
        // for JPA
    }

    private AvailabilityWindow(UUID id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.id = id;
        this.dayOfWeek = dayOfWeek == null ? null : (short) dayOfWeek.getValue();
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * A window on one day of the week, or on every day when {@code dayOfWeek} is null.
     *
     * @throws InvalidAvailabilityWindowException if start and end are the same time
     */
    static AvailabilityWindow of(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        if (startTime.equals(endTime)) {
            throw new InvalidAvailabilityWindowException(
                    "A window that starts and ends at " + startTime + " says nothing about when the item is served");
        }
        return new AvailabilityWindow(UUID.randomUUID(), dayOfWeek, startTime, endTime);
    }

    /**
     * Whether this window covers the given local moment.
     *
     * <p>A window that crosses midnight covers two stretches of a day: from {@code startTime} to the
     * turn of the day, and from the turn of the day to {@code endTime}. The second stretch belongs to
     * the day after the one the window names, which is why the day is checked against the stretch and
     * not against the moment alone.
     *
     * <p>{@code startTime} is inclusive and {@code endTime} exclusive: a window of 18:30 to 23:00
     * serves at 18:30 and no longer serves at 23:00.
     */
    boolean covers(DayOfWeek day, LocalTime time) {
        if (crossesMidnight()) {
            boolean beforeMidnight = !time.isBefore(startTime);
            boolean afterMidnight = time.isBefore(endTime);
            return (beforeMidnight && matchesDay(day)) || (afterMidnight && matchesDay(day.minus(1)));
        }
        return matchesDay(day) && !time.isBefore(startTime) && time.isBefore(endTime);
    }

    private boolean crossesMidnight() {
        return endTime.isBefore(startTime);
    }

    private boolean matchesDay(DayOfWeek day) {
        return dayOfWeek == null || dayOfWeek == day.getValue();
    }

    public DayOfWeek dayOfWeek() {
        return dayOfWeek == null ? null : DayOfWeek.of(dayOfWeek);
    }

    public LocalTime startTime() {
        return startTime;
    }

    public LocalTime endTime() {
        return endTime;
    }
}
