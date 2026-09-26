package br.com.castel.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The behaviour carried by the tab enums, task 2.2 invariants 4, 6, 13, 16, 17 and 20. Every value
 * is listed, so a status added later without a decision fails here instead of slipping through.
 */
class TabStatusTest {

    @Nested
    @DisplayName("TabStatus")
    class OfTheTab {

        @Test
        void shouldAcceptItemsOnlyWhenOpen() {
            assertThat(TabStatus.OPEN.acceptsItems()).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TabStatus.class, names = "OPEN", mode = EXCLUDE)
        void shouldRefuseItemsInAnyOtherStatus(TabStatus status) {
            assertThat(status.acceptsItems()).isFalse();
        }

        @Test
        void shouldAcceptItemCancellationWhenOpen() {
            assertThat(TabStatus.OPEN.acceptsItemCancellation()).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TabStatus.class, names = "OPEN", mode = EXCLUDE)
        void shouldRefuseItemCancellationInAnyOtherStatus(TabStatus status) {
            assertThat(status.acceptsItemCancellation()).isFalse();
        }

        @Test
        void shouldAcceptCancellationOfTheTabWhenOpen() {
            assertThat(TabStatus.OPEN.acceptsCancellation()).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TabStatus.class, names = "OPEN", mode = EXCLUDE)
        void shouldRefuseCancellationOfTheTabInAnyOtherStatus(TabStatus status) {
            assertThat(status.acceptsCancellation()).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TabStatus.class, names = {"OPEN", "CLOSING"})
        void shouldHoldTheTableOrCardWhileOpenOrClosing(TabStatus status) {
            assertThat(status.holdsItsPlace()).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TabStatus.class, names = {"OPEN", "CLOSING"}, mode = EXCLUDE)
        void shouldReleaseTheTableOrCardInAnyOtherStatus(TabStatus status) {
            assertThat(status.holdsItsPlace()).isFalse();
        }
    }

    @Nested
    @DisplayName("TabOrigin")
    class OfTheOrigin {

        @Test
        void shouldChargeServiceByDefaultAtATable() {
            assertThat(TabOrigin.TABLE_SERVICE.chargesServiceByDefault()).isTrue();
        }

        @Test
        void shouldNotChargeServiceByDefaultOnASelfServiceCard() {
            assertThat(TabOrigin.SELF_SERVICE.chargesServiceByDefault()).isFalse();
        }
    }

    @Nested
    @DisplayName("TabItemStatus")
    class OfTheItem {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TabItemStatus.class, names = "CANCELLED", mode = EXCLUDE)
        void shouldAcceptCancellationInAnyStatusButCancelledIncludingDelivered(TabItemStatus status) {
            assertThat(status.acceptsCancellation()).isTrue();
        }

        @Test
        void shouldRefuseCancellationWhenAlreadyCancelled() {
            assertThat(TabItemStatus.CANCELLED.acceptsCancellation()).isFalse();
        }

        /** Assumes "active" means "not cancelled" (see the report): a delivered plate still counts. */
        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TabItemStatus.class, names = "CANCELLED", mode = EXCLUDE)
        void shouldBeActiveInAnyStatusButCancelled(TabItemStatus status) {
            assertThat(status.isActive()).isTrue();
        }

        @Test
        void shouldNotBeActiveOnceCancelled() {
            assertThat(TabItemStatus.CANCELLED.isActive()).isFalse();
        }
    }
}
