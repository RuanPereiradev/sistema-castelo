package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.INACTIVE_DINING_TABLE;
import static br.com.castel.restaurant.domain.TabFixtures.INVALID_CARD_NUMBER;
import static br.com.castel.restaurant.domain.TabFixtures.OPENED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.PROPERTY_ID;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.activeTable;
import static br.com.castel.restaurant.domain.TabFixtures.assertRejectedWith;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.sharedkernel.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Opening a tab, task 2.2 invariants 1 to 3. The one-active-tab rule (invariant 4) lives in the
 * partial unique indexes and is proven by the integration tests, not here.
 */
class TabOpeningTest {

    @Nested
    @DisplayName("on a dining table")
    class OnADiningTable {

        @Test
        void shouldBeBornOpenAsTableServiceHoldingOnlyTheTableId() {
            DiningTable table = activeTable();

            Tab tab = Tab.openForTable(PROPERTY_ID, table, WAITER, OPENED_AT);

            assertThat(tab.status()).isEqualTo(TabStatus.OPEN);
            assertThat(tab.origin()).isEqualTo(TabOrigin.TABLE_SERVICE);
            assertThat(tab.diningTableId()).contains(table.id());
            assertThat(tab.cardNumber()).isEmpty();
            assertThat(tab.propertyId()).isEqualTo(PROPERTY_ID);
            assertThat(tab.openedBy()).isEqualTo(WAITER);
            assertThat(tab.openedAt()).isEqualTo(OPENED_AT);
        }

        @Test
        void shouldRejectAnInactiveDiningTable() {
            DiningTable table = activeTable();
            table.deactivate();

            assertRejectedWith(() -> Tab.openForTable(PROPERTY_ID, table, WAITER, OPENED_AT), INACTIVE_DINING_TABLE);
        }
    }

    @Nested
    @DisplayName("on a self-service card")
    class OnASelfServiceCard {

        @Test
        void shouldBeBornOpenAsSelfServiceHoldingOnlyTheCard() {
            Tab tab = Tab.openForSelfService(PROPERTY_ID, 42, WAITER, OPENED_AT);

            assertThat(tab.status()).isEqualTo(TabStatus.OPEN);
            assertThat(tab.origin()).isEqualTo(TabOrigin.SELF_SERVICE);
            assertThat(tab.cardNumber()).contains(42);
            assertThat(tab.diningTableId()).isEmpty();
            assertThat(tab.openedBy()).isEqualTo(WAITER);
            assertThat(tab.openedAt()).isEqualTo(OPENED_AT);
        }

        @ParameterizedTest(name = "card {0}")
        @ValueSource(ints = {1, 999})
        void shouldAcceptTheCardsAtTheEdgesOfTheRange(int cardNumber) {
            Tab tab = Tab.openForSelfService(PROPERTY_ID, cardNumber, WAITER, OPENED_AT);

            assertThat(tab.cardNumber()).contains(cardNumber);
        }

        @ParameterizedTest(name = "card {0}")
        @ValueSource(ints = {-1, 0, 1000})
        void shouldRejectACardOutsideOneTo999(int cardNumber) {
            assertRejectedWith(
                    () -> Tab.openForSelfService(PROPERTY_ID, cardNumber, WAITER, OPENED_AT), INVALID_CARD_NUMBER);
        }
    }

    @Nested
    @DisplayName("public token")
    class PublicToken {

        @Test
        void shouldIssueARandomVersion4TokenDifferentFromTheId() {
            Tab tab = TabFixtures.tableTab();

            assertThat(tab.publicToken()).isNotNull();
            assertThat(tab.publicToken().version()).isEqualTo(4);
            assertThat(tab.publicToken()).isNotEqualTo(tab.id().value());
        }

        @Test
        void shouldIssueADifferentTokenForEachTab() {
            Tab first = TabFixtures.tableTab();
            Tab second = TabFixtures.cardTab();

            assertThat(first.publicToken()).isNotEqualTo(second.publicToken());
        }
    }

    @Test
    void shouldOpenWithNoItemsAndAZeroSubtotal() {
        Tab tab = TabFixtures.tableTab();

        assertThat(tab.items()).isEmpty();
        assertThat(tab.subtotal()).isEqualTo(Money.ZERO);
    }
}
