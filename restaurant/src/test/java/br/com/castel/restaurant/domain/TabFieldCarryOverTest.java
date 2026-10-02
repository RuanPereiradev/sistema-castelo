package br.com.castel.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The guard over what survives a change of table.
 *
 * <p>A change of table opens a new tab on the destination and absorbs the current one (decision
 * T5), so every field of {@link Tab} has to be told whether it travels. Three bugs were found one
 * at a time because of that: the number of guests (T17), and the split of each line and the service
 * charge the operator had turned off (T18). Each was a field nobody remembered to carry, and each
 * was found by a different person after the code was already written.
 *
 * <p>This test makes the next one impossible to miss. It reads the declared fields of {@code Tab}
 * by reflection and checks them against the classification below, so <b>adding a field to
 * {@code Tab} fails this test</b> until the field is named here as either carried or left behind.
 * The failure message is the whole point: it asks the one question that was forgotten three times.
 *
 * <p>It is a structural test, like the ArchUnit rules of the {@code app} module: it protects a
 * decision about the shape of the code, not a calculation.
 */
class TabFieldCarryOverTest {

    /** What the move exists to change: where the tab sits, and its own identity. */
    private static final Set<String> WHERE_IT_SITS =
            Set.of("id", "origin", "diningTableId", "cardNumber", "publicToken");

    /** What the operator chose, or what is a fact about the party. Losing one of these is a bug. */
    private static final Set<String> CARRIED_OVER =
            Set.of("propertyId", "openedBy", "openedAt", "serviceChargeApplied", "guestCount", "items");

    /** Null on an {@code OPEN} tab by construction, so there is nothing to carry. */
    private static final Set<String> SETTLING_THE_BILL = Set.of(
            "status",
            "folioId",
            "tabChargeId",
            "destination",
            "serviceChargeRateFraction",
            "closingStartedAt",
            "closingStartedBy",
            "closedAt",
            "closedBy",
            "cancelledAt",
            "cancelledBy",
            "cancellationReason",
            "mergedIntoTabId",
            "mergedAt",
            "mergedBy");

    @Test
    @DisplayName("every field of Tab is classified, so a new one cannot be silently forgotten")
    void shouldClassifyEveryFieldOfTheTab() {
        List<String> declared = stateFieldsOf(Tab.class);

        List<String> unclassified = declared.stream()
                .filter(field -> !WHERE_IT_SITS.contains(field))
                .filter(field -> !CARRIED_OVER.contains(field))
                .filter(field -> !SETTLING_THE_BILL.contains(field))
                .toList();

        assertThat(unclassified)
                .as("""
                        A field was added to Tab and nobody said what a change of table does with it.
                        Decide, and add it to one of the three sets of this test:
                          WHERE_IT_SITS      - what the move exists to change; never carried
                          CARRIED_OVER       - what the operator chose; Tab.continuationOf must copy it
                          SETTLING_THE_BILL  - null on an OPEN tab, so there is nothing to carry
                        Getting this wrong is how T17 and T18 happened: the value falls in silence.""")
                .isEmpty();
    }

    @Test
    @DisplayName("nothing is classified that is not a field of Tab any more")
    void shouldNotClassifyFieldsThatNoLongerExist() {
        List<String> declared = stateFieldsOf(Tab.class);

        List<String> stale = Stream.of(WHERE_IT_SITS, CARRIED_OVER, SETTLING_THE_BILL)
                .flatMap(Set::stream)
                .filter(field -> !declared.contains(field))
                .toList();

        assertThat(stale)
                .as("a field was renamed or removed from Tab; this test still names the old one")
                .isEmpty();
    }

    /** Every field that holds state: the constants and the comparator are not state. */
    private static List<String> stateFieldsOf(Class<?> type) {
        return Stream.of(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .toList();
    }
}
