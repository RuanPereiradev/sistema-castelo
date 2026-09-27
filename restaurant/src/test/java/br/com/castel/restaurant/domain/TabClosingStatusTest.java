package br.com.castel.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What each {@link TabStatus} accepts in the closing of task 3.2 (section 4). Every status is listed
 * against every question, so a status added later without a decision fails here.
 */
class TabClosingStatusTest {

    private static final boolean Y = true;
    private static final boolean N = false;

    /** Columns: OPEN, CLOSING, CLOSED, CANCELLED, MERGED. */
    static Stream<Arguments> matrix() {
        return Stream.of(
                row("serviceChargeChange", TabStatus::acceptsServiceChargeChange, Y, N, N, N, N),
                row("closing", TabStatus::acceptsClosing, Y, N, N, N, N),
                row("reopening", TabStatus::acceptsReopening, N, Y, N, N, N),
                row("payment", TabStatus::acceptsPayment, N, Y, Y, N, N),
                row("settlement", TabStatus::acceptsSettlement, N, Y, N, N, N),
                row("splitChange", TabStatus::acceptsSplitChange, Y, Y, N, N, N),
                row("items", TabStatus::acceptsItems, Y, N, N, N, N),
                row("itemCancellation", TabStatus::acceptsItemCancellation, Y, N, N, N, N),
                row("cancellation", TabStatus::acceptsCancellation, Y, N, N, N, N),
                row("holdsItsPlace", TabStatus::holdsItsPlace, Y, Y, N, N, N));
    }

    private static Arguments row(String question, Predicate<TabStatus> accepts, boolean... expected) {
        return Arguments.of(question, accepts, expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    void shouldAnswerEachClosingQuestionForEveryStatus(String question, Predicate<TabStatus> accepts,
            boolean[] expected) {
        TabStatus[] statuses = {TabStatus.OPEN, TabStatus.CLOSING, TabStatus.CLOSED, TabStatus.CANCELLED,
                TabStatus.MERGED};

        assertThat(TabStatus.values()).containsExactlyInAnyOrder(statuses);
        assertThat(Stream.of(statuses).map(accepts::test).toList())
                .as(question)
                .containsExactly(expected[0], expected[1], expected[2], expected[3], expected[4]);
    }
}
