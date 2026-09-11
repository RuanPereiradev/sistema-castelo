package br.com.castel.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateRangeTest {

    private static final LocalDate OCT_01 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_02 = LocalDate.of(2026, 10, 2);
    private static final LocalDate OCT_03 = LocalDate.of(2026, 10, 3);
    private static final LocalDate OCT_04 = LocalDate.of(2026, 10, 4);
    private static final LocalDate OCT_05 = LocalDate.of(2026, 10, 5);
    private static final LocalDate OCT_06 = LocalDate.of(2026, 10, 6);
    private static final LocalDate OCT_07 = LocalDate.of(2026, 10, 7);
    private static final LocalDate OCT_10 = LocalDate.of(2026, 10, 10);
    private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);

    // ---------------------------------------------------------------------
    // Nights and validation
    // ---------------------------------------------------------------------

    @Test
    void shouldCountExactlyThreeNightsFromFirstToFourth() {
        DateRange range = DateRange.of(OCT_01, OCT_04);

        assertThat(range.nights()).isEqualTo(3);
    }

    @Test
    void shouldRejectDateRangeWhenEndEqualsStart() {
        assertThatThrownBy(() -> DateRange.of(OCT_01, OCT_01))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void shouldRejectDateRangeWhenEndIsBeforeStart() {
        assertThatThrownBy(() -> DateRange.of(OCT_04, OCT_01))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    // ---------------------------------------------------------------------
    // dates()
    // ---------------------------------------------------------------------

    @Test
    void shouldListEachNightDateInAscendingOrder() {
        DateRange range = DateRange.of(OCT_01, OCT_04);

        assertThat(range.dates()).containsExactly(OCT_01, OCT_02, OCT_03);
    }

    @Test
    void shouldNotIncludeEndDateInDates() {
        DateRange range = DateRange.of(OCT_01, OCT_04);

        assertThat(range.dates()).doesNotContain(OCT_04);
    }

    // ---------------------------------------------------------------------
    // contains()
    // ---------------------------------------------------------------------

    @Test
    void shouldContainStartDate() {
        DateRange range = DateRange.of(OCT_01, OCT_04);

        assertThat(range.contains(OCT_01)).isTrue();
    }

    @Test
    void shouldContainLastNightBeforeEnd() {
        DateRange range = DateRange.of(OCT_01, OCT_04);

        assertThat(range.contains(OCT_03)).isTrue();
    }

    @Test
    void shouldNotContainEndDate() {
        DateRange range = DateRange.of(OCT_01, OCT_04);

        assertThat(range.contains(OCT_04)).isFalse();
    }

    @Test
    void shouldNotContainDateBeforeStart() {
        DateRange range = DateRange.of(OCT_01, OCT_04);

        assertThat(range.contains(SEP_30)).isFalse();
    }

    // ---------------------------------------------------------------------
    // overlaps()
    // ---------------------------------------------------------------------

    @Test
    void shouldOverlapWhenRangesPartiallyOverlap() {
        DateRange first = DateRange.of(OCT_01, OCT_04);
        DateRange second = DateRange.of(OCT_03, OCT_06);

        assertThat(first.overlaps(second)).isTrue();
    }

    @Test
    void shouldOverlapWhenRangesPartiallyOverlapInReverseOrder() {
        DateRange first = DateRange.of(OCT_01, OCT_04);
        DateRange second = DateRange.of(OCT_03, OCT_06);

        assertThat(second.overlaps(first)).isTrue();
    }

    @Test
    void shouldNotOverlapWhenOneEndsExactlyWhereTheOtherStarts() {
        DateRange first = DateRange.of(OCT_01, OCT_04);
        DateRange second = DateRange.of(OCT_04, OCT_07);

        assertThat(first.overlaps(second)).isFalse();
    }

    @Test
    void shouldNotOverlapWhenOneStartsExactlyWhereTheOtherEnds() {
        DateRange first = DateRange.of(OCT_01, OCT_04);
        DateRange second = DateRange.of(OCT_04, OCT_07);

        assertThat(second.overlaps(first)).isFalse();
    }

    @Test
    void shouldOverlapWhenOuterRangeContainsInnerRange() {
        DateRange outer = DateRange.of(OCT_01, OCT_10);
        DateRange inner = DateRange.of(OCT_03, OCT_05);

        assertThat(outer.overlaps(inner)).isTrue();
    }

    @Test
    void shouldOverlapWhenInnerRangeIsContainedByOuterRange() {
        DateRange outer = DateRange.of(OCT_01, OCT_10);
        DateRange inner = DateRange.of(OCT_03, OCT_05);

        assertThat(inner.overlaps(outer)).isTrue();
    }

    @Test
    void shouldNotOverlapWhenThereIsAGapBetweenRanges() {
        DateRange first = DateRange.of(OCT_01, OCT_03);
        DateRange second = DateRange.of(OCT_05, OCT_07);

        assertThat(first.overlaps(second)).isFalse();
    }

    // ---------------------------------------------------------------------
    // Single night
    // ---------------------------------------------------------------------

    @Test
    void shouldCountOneNightForSingleNightRange() {
        DateRange range = DateRange.of(OCT_01, OCT_02);

        assertThat(range.nights()).isEqualTo(1);
    }

    @Test
    void shouldListOnlyStartDateForSingleNightRange() {
        DateRange range = DateRange.of(OCT_01, OCT_02);

        assertThat(range.dates()).containsExactly(OCT_01);
    }

    // ---------------------------------------------------------------------
    // Year boundary
    // ---------------------------------------------------------------------

    @Test
    void shouldCountNightsAcrossYearBoundary() {
        DateRange range = DateRange.of(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2));

        assertThat(range.nights()).isEqualTo(3);
    }

    @Test
    void shouldListDatesAcrossYearBoundary() {
        DateRange range = DateRange.of(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2));

        assertThat(range.dates()).containsExactly(
                LocalDate.of(2026, 12, 30),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2027, 1, 1));
    }
}
