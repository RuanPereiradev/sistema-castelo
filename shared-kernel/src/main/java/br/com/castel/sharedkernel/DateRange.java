package br.com.castel.sharedkernel;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Stay period as the half-open interval {@code [start, end)}.
 *
 * <p>The end date is the check-out day and is not a night: 01/10 to 04/10 is three nights
 * (01, 02 and 03).
 *
 * <p>A range has from 1 to 365 nights. Every rejection is an {@link InvalidDateRangeException}; its
 * message never includes the rejected dates.
 */
public final class DateRange {

    private static final long MAXIMUM_NIGHTS = 365;

    private final LocalDate start;
    private final LocalDate end;

    private DateRange(LocalDate start, LocalDate end) {
        this.start = start;
        this.end = end;
    }

    public static DateRange of(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new InvalidDateRangeException("Start and end must not be null");
        }
        if (!end.isAfter(start)) {
            throw new InvalidDateRangeException("End must be after start");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAXIMUM_NIGHTS) {
            throw new InvalidDateRangeException("Date range must not exceed " + MAXIMUM_NIGHTS + " nights");
        }
        return new DateRange(start, end);
    }

    public LocalDate start() {
        return start;
    }

    /** Exclusive end: the check-out day. */
    public LocalDate end() {
        return end;
    }

    public int nights() {
        return Math.toIntExact(ChronoUnit.DAYS.between(start, end));
    }

    public boolean contains(LocalDate date) {
        if (date == null) {
            throw new InvalidDateRangeException("Date must not be null");
        }
        return !date.isBefore(start) && date.isBefore(end);
    }

    public boolean overlaps(DateRange other) {
        if (other == null) {
            throw new InvalidDateRangeException("Date range must not be null");
        }
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    /** Each night of the stay, in ascending order, excluding the end date. */
    public Stream<LocalDate> dates() {
        return start.datesUntil(end);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof DateRange range && start.equals(range.start) && end.equals(range.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "[" + start + ", " + end + ")";
    }
}
