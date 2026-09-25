package br.com.castel.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.sharedkernel.DomainException;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The restaurant's dining tables (invariants 2 to 5 of task 1.5). Label uniqueness (invariant 7) is
 * checked by the use case and is not covered here.
 */
class DiningTableTest {

    private static final UUID PROPERTY_ID = UUID.randomUUID();

    private static final String INVALID_DINING_TABLE_LABEL = "INVALID_DINING_TABLE_LABEL";
    private static final String INVALID_DINING_TABLE_SEATS = "INVALID_DINING_TABLE_SEATS";
    private static final String INVALID_DINING_TABLE_AREA = "INVALID_DINING_TABLE_AREA";

    private static DiningTable verandaTable() {
        return DiningTable.create(PROPERTY_ID, "V1", 4, "Varanda");
    }

    private static void assertRejectedWith(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(DomainException.class, failure -> assertThat(failure.code()).isEqualTo(code));
    }

    @Nested
    @DisplayName("label (invariant 2)")
    class Label {

        @Test
        void shouldTrimTheLabel() {
            DiningTable table = DiningTable.create(PROPERTY_ID, "  Mesa 5  ", null, null);

            assertThat(table.label()).isEqualTo("Mesa 5");
        }

        @Test
        void shouldAcceptALabelOfExactlyTwentyCharacters() {
            DiningTable table = DiningTable.create(PROPERTY_ID, "x".repeat(20), null, null);

            assertThat(table.label()).hasSize(20);
        }

        @Test
        void shouldRejectALabelLongerThanTwentyCharacters() {
            assertRejectedWith(
                    () -> DiningTable.create(PROPERTY_ID, "x".repeat(21), null, null), INVALID_DINING_TABLE_LABEL);
        }

        @Test
        void shouldRejectABlankLabel() {
            assertRejectedWith(() -> DiningTable.create(PROPERTY_ID, "   ", null, null), INVALID_DINING_TABLE_LABEL);
        }

        @Test
        void shouldRejectAMissingLabelAsBlank() {
            assertRejectedWith(() -> DiningTable.create(PROPERTY_ID, null, null, null), INVALID_DINING_TABLE_LABEL);
        }

        @Test
        void shouldReportTheLabelFirstWhenEveryFieldIsInvalid() {
            assertRejectedWith(
                    () -> DiningTable.create(PROPERTY_ID, "   ", 0, "x".repeat(51)), INVALID_DINING_TABLE_LABEL);
        }
    }

    @Nested
    @DisplayName("seats (invariant 3)")
    class Seats {

        @Test
        void shouldAcceptATableWithoutSeats() {
            DiningTable table = DiningTable.create(PROPERTY_ID, "Mesa 1", null, null);

            assertThat(table.seats()).isEmpty();
        }

        @Test
        void shouldAcceptOneSeat() {
            DiningTable table = DiningTable.create(PROPERTY_ID, "Mesa 1", 1, null);

            assertThat(table.seats()).contains(1);
        }

        @Test
        void shouldAcceptNineHundredNinetyNineSeats() {
            DiningTable table = DiningTable.create(PROPERTY_ID, "Mesa 1", 999, null);

            assertThat(table.seats()).contains(999);
        }

        @Test
        void shouldRejectZeroSeats() {
            assertRejectedWith(() -> DiningTable.create(PROPERTY_ID, "Mesa 1", 0, null), INVALID_DINING_TABLE_SEATS);
        }

        @Test
        void shouldRejectOneThousandSeats() {
            assertRejectedWith(
                    () -> DiningTable.create(PROPERTY_ID, "Mesa 1", 1000, null), INVALID_DINING_TABLE_SEATS);
        }
    }

    @Nested
    @DisplayName("area (invariant 4)")
    class Area {

        @Test
        void shouldTreatABlankAreaAsNoArea() {
            DiningTable table = DiningTable.create(PROPERTY_ID, "Mesa 1", null, "   ");

            assertThat(table.area()).isEmpty();
        }

        @Test
        void shouldTrimTheArea() {
            DiningTable table = DiningTable.create(PROPERTY_ID, "V1", null, "  Varanda  ");

            assertThat(table.area()).contains("Varanda");
        }

        @Test
        void shouldAcceptAnAreaOfExactlyFiftyCharacters() {
            DiningTable table = DiningTable.create(PROPERTY_ID, "Mesa 1", null, "x".repeat(50));

            assertThat(table.area()).hasValueSatisfying(area -> assertThat(area).hasSize(50));
        }

        @Test
        void shouldRejectAnAreaLongerThanFiftyCharacters() {
            assertRejectedWith(
                    () -> DiningTable.create(PROPERTY_ID, "Mesa 1", null, "x".repeat(51)), INVALID_DINING_TABLE_AREA);
        }
    }

    @Nested
    @DisplayName("redescribe (decision #7)")
    class Redescribe {

        @Test
        void shouldReplaceAllThreeFields() {
            DiningTable table = verandaTable();

            table.redescribe("S2", 6, "Salão");

            assertThat(table.label()).isEqualTo("S2");
            assertThat(table.seats()).contains(6);
            assertThat(table.area()).contains("Salão");
        }

        @Test
        void shouldClearSeatsAndAreaWhenTheyAreNotInformed() {
            DiningTable table = verandaTable();

            table.redescribe("V1", null, null);

            assertThat(table.seats()).isEmpty();
            assertThat(table.area()).isEmpty();
        }

        @Test
        void shouldRejectAnInvalidRedescriptionAndKeepThePreviousDescription() {
            DiningTable table = verandaTable();

            assertRejectedWith(() -> table.redescribe("S2", 0, "Salão"), INVALID_DINING_TABLE_SEATS);

            assertThat(table.label()).isEqualTo("V1");
            assertThat(table.seats()).contains(4);
            assertThat(table.area()).contains("Varanda");
        }
    }

    @Nested
    @DisplayName("activation (invariant 5)")
    class Activation {

        @Test
        void shouldBeBornActive() {
            DiningTable table = verandaTable();

            assertThat(table.isActive()).isTrue();
        }

        @Test
        void shouldAcceptDeactivatingTwice() {
            DiningTable table = verandaTable();
            table.deactivate();

            table.deactivate();

            assertThat(table.isActive()).isFalse();
        }

        @Test
        void shouldAcceptActivatingAnActiveTable() {
            DiningTable table = verandaTable();

            table.activate();

            assertThat(table.isActive()).isTrue();
        }

        @Test
        void shouldBeActiveAgainWhenReactivated() {
            DiningTable table = verandaTable();
            table.deactivate();

            table.activate();

            assertThat(table.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("listing order (decision #8)")
    class ListingOrder {

        @Test
        void shouldGroupByAreaWithTablesWithoutAreaLastAndSortLabelsNaturally() {
            DiningTable noArea = DiningTable.create(PROPERTY_ID, "Balcao", null, null);
            DiningTable hallTen = DiningTable.create(PROPERTY_ID, "Mesa 10", 4, "Salao");
            DiningTable hallTwo = DiningTable.create(PROPERTY_ID, "Mesa 2", 4, "Salao");
            DiningTable veranda = DiningTable.create(PROPERTY_ID, "V1", 2, "Varanda");

            List<DiningTable> listed = List.of(noArea, hallTen, veranda, hallTwo).stream()
                    .sorted(DiningTable.listingOrder())
                    .toList();

            assertThat(listed).containsExactly(hallTwo, hallTen, veranda, noArea);
        }

        @Test
        void shouldCompareNumbersByValueIgnoringCaseAndLeadingZeros() {
            DiningTable ten = DiningTable.create(PROPERTY_ID, "Mesa 10", null, null);
            DiningTable threeLowerCase = DiningTable.create(PROPERTY_ID, "mesa 3", null, null);
            DiningTable twoWithZero = DiningTable.create(PROPERTY_ID, "Mesa 02", null, null);
            DiningTable two = DiningTable.create(PROPERTY_ID, "Mesa 2", null, null);

            List<DiningTable> listed = List.of(ten, threeLowerCase, twoWithZero, two).stream()
                    .sorted(DiningTable.listingOrder())
                    .toList();

            assertThat(listed).containsExactly(twoWithZero, two, threeLowerCase, ten);
        }

        @Test
        void shouldTreatAreasThatDifferOnlyInCaseOrAccentsAsOneGroupInReadingOrder() {
            DiningTable outsideAccented = DiningTable.create(PROPERTY_ID, "E2", null, "Área externa");
            DiningTable outsidePlain = DiningTable.create(PROPERTY_ID, "E1", null, "area externa");
            DiningTable verandaTwo = DiningTable.create(PROPERTY_ID, "V2", null, "Varanda");
            DiningTable verandaOne = DiningTable.create(PROPERTY_ID, "V1", null, "varanda");
            DiningTable verandaThree = DiningTable.create(PROPERTY_ID, "V3", null, "varanda");

            List<DiningTable> listed = List.of(verandaTwo, outsideAccented, verandaThree, verandaOne, outsidePlain)
                    .stream()
                    .sorted(DiningTable.listingOrder())
                    .toList();

            assertThat(listed).containsExactly(outsidePlain, outsideAccented, verandaOne, verandaTwo, verandaThree);
        }
    }
}
