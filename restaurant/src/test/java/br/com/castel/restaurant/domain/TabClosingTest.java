package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.FakeTabBilling.FOLIO_BALANCE_NOT_ZERO;
import static br.com.castel.restaurant.domain.TabFixtures.CANCELLED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.CLOSED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.CLOSING_AT;
import static br.com.castel.restaurant.domain.TabFixtures.FORTALEZA;
import static br.com.castel.restaurant.domain.TabFixtures.INVALID_CANCELLATION_REASON;
import static br.com.castel.restaurant.domain.TabFixtures.INVALID_REOPENING_REASON;
import static br.com.castel.restaurant.domain.TabFixtures.ORDERED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.OTHER_WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_HAS_ACTIVE_ITEMS;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_HAS_NO_ACTIVE_ITEMS;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_NOT_CLOSING;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_NOT_OPEN;
import static br.com.castel.restaurant.domain.TabFixtures.TEN_PERCENT;
import static br.com.castel.restaurant.domain.TabFixtures.TWENTY_PERCENT;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.assertRejectedWith;
import static br.com.castel.restaurant.domain.TabFixtures.buffet;
import static br.com.castel.restaurant.domain.TabFixtures.cancelledTab;
import static br.com.castel.restaurant.domain.TabFixtures.cardTab;
import static br.com.castel.restaurant.domain.TabFixtures.closedTab;
import static br.com.castel.restaurant.domain.TabFixtures.closingTab;
import static br.com.castel.restaurant.domain.TabFixtures.dish;
import static br.com.castel.restaurant.domain.TabFixtures.grams;
import static br.com.castel.restaurant.domain.TabFixtures.oneUnit;
import static br.com.castel.restaurant.domain.TabFixtures.orderDish;
import static br.com.castel.restaurant.domain.TabFixtures.tableTab;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.billing.api.ReceivedPaymentView;
import br.com.castel.sharedkernel.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Closing a tab through its folio, task 3.2 invariants 11 to 17, against a fake billing. */
class TabClosingTest {

    private static final String REOPENING_REASON = "Cliente pediu sobremesa";
    private static final String CANCELLATION_REASON = "Cliente foi embora";

    private final FakeTabBilling billing = new FakeTabBilling();

    private static void pay(Tab tab, String amount, FakeTabBilling billing) {
        tab.receivePayment(PaymentMethod.PIX, Money.of(amount), "key-" + amount, billing);
    }

    @Nested
    @DisplayName("starting to close")
    class StartingToClose {

        @Test
        void shouldPostTheFrozenTotalAsOneChargeOnANewTabFolio() {
            Tab tab = tableTab();
            orderDish(tab, "100.00");
            orderDish(tab, "33.35");

            tab.startClosing(TEN_PERCENT, billing, OTHER_WAITER, CLOSING_AT);

            assertThat(billing.openedFolios).hasSize(1);
            assertThat(billing.postings).singleElement().satisfies(posting -> {
                assertThat(posting.folioId()).isEqualTo(billing.openedFolios.get(0));
                assertThat(posting.tabId()).isEqualTo(tab.id());
                assertThat(posting.amount()).isEqualTo(Money.of("146.69"));
            });
        }

        @Test
        void shouldMoveToClosingRecordingFolioChargeRateWhoAndWhen() {
            Tab tab = tableTab();
            orderDish(tab, "100.00");

            tab.startClosing(TEN_PERCENT, billing, OTHER_WAITER, CLOSING_AT);

            assertThat(tab.status()).isEqualTo(TabStatus.CLOSING);
            assertThat(tab.folioId()).contains(billing.openedFolios.get(0));
            assertThat(tab.tabChargeId()).contains(billing.postings.get(0).chargeId());
            assertThat(tab.serviceChargeRate()).contains(TEN_PERCENT);
            assertThat(tab.closingStartedBy()).contains(OTHER_WAITER);
            assertThat(tab.closingStartedAt()).contains(CLOSING_AT);
            assertThat(tab.destination()).isEmpty();
        }

        @Test
        void shouldDescribeTheChargeOfATableTabByItsTable() {
            Tab tab = tableTab();
            orderDish(tab, "10.00");

            tab.startClosing(TEN_PERCENT, billing, WAITER, CLOSING_AT);

            assertThat(billing.postings.get(0).description())
                    .isEqualTo("Tab table " + tab.diningTableId().orElseThrow().value());
        }

        @Test
        void shouldDescribeTheChargeOfASelfServiceTabByItsCard() {
            Tab tab = cardTab();
            tab.addItem(buffet(), grams(500), WAITER, ORDERED_AT, FORTALEZA);

            tab.startClosing(TEN_PERCENT, billing, WAITER, CLOSING_AT);

            assertThat(billing.postings.get(0).description()).isEqualTo("Tab card 42");
        }

        @Test
        void shouldRefuseATabWithoutActiveItemsBeforeTouchingTheBilling() {
            Tab tab = tableTab();
            TabItem item = orderDish(tab, "10.00");
            tab.cancelItem(item.id(), "Cliente desistiu", WAITER, CANCELLED_AT);

            assertRejectedWith(() -> tab.startClosing(TEN_PERCENT, billing, WAITER, CLOSING_AT), TAB_HAS_NO_ACTIVE_ITEMS);
            assertThat(tab.status()).isEqualTo(TabStatus.OPEN);
            assertThat(tab.serviceChargeRate()).isEmpty();
            assertThat(billing.openedFolios).isEmpty();
        }

        @Test
        void shouldCheckTheStatusBeforeTheItems() {
            Tab tab = cancelledTab();

            assertRejectedWith(() -> tab.startClosing(TEN_PERCENT, billing, WAITER, CLOSING_AT), TAB_NOT_OPEN);
            assertThat(billing.openedFolios).isEmpty();
        }

        @Test
        void shouldRefuseStartingToCloseTwice() {
            Tab tab = closingTab(billing);

            assertRejectedWith(() -> tab.startClosing(TEN_PERCENT, billing, WAITER, CLOSING_AT), TAB_NOT_OPEN);
            assertThat(billing.postings).hasSize(1);
        }
    }

    @Nested
    @DisplayName("while closing")
    class WhileClosing {

        @Test
        void shouldRefuseANewItem() {
            Tab tab = closingTab(billing);

            assertRejectedWith(() -> tab.addItem(dish("5.00"), oneUnit(), WAITER, ORDERED_AT, FORTALEZA), TAB_NOT_OPEN);
        }

        @Test
        void shouldRefuseCancellingAnItem() {
            Tab tab = closingTab(billing);
            TabItem item = tab.items().get(0);

            assertRejectedWith(() -> tab.cancelItem(item.id(), "Engano", WAITER, CANCELLED_AT), TAB_NOT_OPEN);
        }

        @Test
        void shouldRefuseCancellingTheTab() {
            Tab tab = closingTab(billing);

            assertRejectedWith(() -> tab.cancel(CANCELLATION_REASON, billing, WAITER, CANCELLED_AT), TAB_NOT_OPEN);
            assertThat(billing.closedFolios).isEmpty();
        }

        @Test
        void shouldKeepHoldingTheTableOrCard() {
            Tab tab = closingTab(billing);

            assertThat(tab.status().holdsItsPlace()).isTrue();
        }
    }

    @Nested
    @DisplayName("receiving a payment")
    class ReceivingAPayment {

        @Test
        void shouldDelegateThePaymentToTheFolioWhileClosing() {
            Tab tab = closingTab(billing);

            ReceivedPaymentView payment = tab.receivePayment(PaymentMethod.CREDIT_CARD, Money.of("40.00"), "key-1", billing);

            assertThat(payment.amount()).isEqualTo(Money.of("40.00"));
            assertThat(billing.balanceOf(tab.folioId().orElseThrow())).isEqualTo(Money.of("70.00"));
        }

        @Test
        void shouldStillDelegateOnAClosedTabSoTheFolioAnswersTheReplay() {
            Tab tab = closedTab(billing);

            tab.receivePayment(PaymentMethod.PIX, Money.of("110.00"), "key-closed", billing);

            assertThat(billing.payments).hasSize(2);
        }

        @Test
        void shouldRefuseAPaymentOnAnOpenTab() {
            Tab tab = tableTab();
            orderDish(tab, "10.00");

            assertRejectedWith(() -> pay(tab, "10.00", billing), TAB_NOT_CLOSING);
            assertThat(billing.payments).isEmpty();
        }

        @Test
        void shouldRefuseAPaymentOnACancelledTab() {
            Tab tab = cancelledTab();

            assertRejectedWith(() -> pay(tab, "10.00", billing), TAB_NOT_CLOSING);
        }
    }

    @Nested
    @DisplayName("reopening")
    class Reopening {

        @Test
        void shouldReverseTheChargeInForceWithTheReason() {
            Tab tab = closingTab(billing);
            var charge = tab.tabChargeId().orElseThrow();

            tab.reopen(REOPENING_REASON, billing);

            assertThat(billing.reversals).singleElement().satisfies(reversal -> {
                assertThat(reversal.folioId()).isEqualTo(tab.folioId().orElseThrow());
                assertThat(reversal.chargeId()).isEqualTo(charge);
                assertThat(reversal.reason()).isEqualTo(REOPENING_REASON);
            });
        }

        @Test
        void shouldGoBackToOpenClearingChargeRateAndClosingStartedButKeepingTheFolio() {
            Tab tab = closingTab(billing);

            tab.reopen(REOPENING_REASON, billing);

            assertThat(tab.status()).isEqualTo(TabStatus.OPEN);
            assertThat(tab.tabChargeId()).isEmpty();
            assertThat(tab.serviceChargeRate()).isEmpty();
            assertThat(tab.closingStartedAt()).isEmpty();
            assertThat(tab.closingStartedBy()).isEmpty();
            assertThat(tab.folioId()).contains(billing.openedFolios.get(0));
        }

        @Test
        void shouldReverseWithTheTrimmedReason() {
            Tab tab = closingTab(billing);

            tab.reopen("  " + REOPENING_REASON + "  ", billing);

            assertThat(billing.reversals.get(0).reason()).isEqualTo(REOPENING_REASON);
        }

        @Test
        void shouldAcceptAReasonOfExactlyFiveHundredCharactersAfterTrimming() {
            Tab tab = closingTab(billing);

            tab.reopen(" " + "a".repeat(500) + " ", billing);

            assertThat(tab.status()).isEqualTo(TabStatus.OPEN);
        }

        @Test
        void shouldRefuseAReasonAboveFiveHundredCharactersWithoutReversing() {
            Tab tab = closingTab(billing);

            assertRejectedWith(() -> tab.reopen("a".repeat(501), billing), INVALID_REOPENING_REASON);
            assertThat(tab.status()).isEqualTo(TabStatus.CLOSING);
            assertThat(billing.reversals).isEmpty();
        }

        @Test
        void shouldRefuseABlankReason() {
            Tab tab = closingTab(billing);

            assertRejectedWith(() -> tab.reopen("   ", billing), INVALID_REOPENING_REASON);
        }

        @Test
        void shouldRefuseAMissingReason() {
            Tab tab = closingTab(billing);

            assertRejectedWith(() -> tab.reopen(null, billing), INVALID_REOPENING_REASON);
        }

        @Test
        void shouldCheckTheStatusBeforeTheReason() {
            Tab tab = tableTab();

            assertRejectedWith(() -> tab.reopen(null, billing), TAB_NOT_CLOSING);
        }

        @Test
        void shouldRefuseReopeningAClosedTab() {
            Tab tab = closedTab(billing);

            assertRejectedWith(() -> tab.reopen(REOPENING_REASON, billing), TAB_NOT_CLOSING);
            assertThat(tab.status()).isEqualTo(TabStatus.CLOSED);
        }

        @Test
        void shouldAcceptNewItemsOnceReopened() {
            Tab tab = closingTab(billing);
            tab.reopen(REOPENING_REASON, billing);

            orderDish(tab, "20.00");

            assertThat(tab.subtotal()).isEqualTo(Money.of("120.00"));
        }

        @Test
        void shouldReuseTheFolioAndPostTheNewTotalWhenClosingAgain() {
            Tab tab = closingTab(billing);
            tab.reopen(REOPENING_REASON, billing);
            orderDish(tab, "20.00");

            tab.startClosing(TWENTY_PERCENT, billing, WAITER, CLOSING_AT);

            assertThat(billing.openedFolios).hasSize(1);
            assertThat(billing.postings).hasSize(2);
            assertThat(billing.postings.get(1).folioId()).isEqualTo(billing.openedFolios.get(0));
            assertThat(billing.postings.get(1).amount()).isEqualTo(Money.of("144.00"));
            assertThat(tab.tabChargeId()).contains(billing.postings.get(1).chargeId());
            assertThat(tab.serviceChargeRate()).contains(TWENTY_PERCENT);
        }

        @Test
        void shouldKeepAPaymentAsCreditOnTheFolioAcrossTheReopening() {
            Tab tab = closingTab(billing);
            pay(tab, "10.00", billing);
            tab.reopen(REOPENING_REASON, billing);
            orderDish(tab, "20.00");

            tab.startClosing(TEN_PERCENT, billing, WAITER, CLOSING_AT);

            // new total 132.00 minus the 10.00 already paid
            assertThat(billing.balanceOf(tab.folioId().orElseThrow())).isEqualTo(Money.of("122.00"));
        }
    }

    @Nested
    @DisplayName("closing")
    class Closing {

        @Test
        void shouldCloseTheTabAndItsFolioOnceFullyPaid() {
            Tab tab = closingTab(billing);
            pay(tab, "60.00", billing);
            pay(tab, "50.00", billing);

            tab.close(billing, OTHER_WAITER, CLOSED_AT);

            assertThat(tab.status()).isEqualTo(TabStatus.CLOSED);
            assertThat(billing.closedFolios).containsExactly(tab.folioId().orElseThrow());
            assertThat(tab.destination()).contains(TabDestination.DIRECT_PAYMENT);
            assertThat(tab.closedBy()).contains(OTHER_WAITER);
            assertThat(tab.closedAt()).contains(CLOSED_AT);
        }

        @Test
        void shouldReleaseTheTableOrCardOnceClosed() {
            Tab tab = closedTab(billing);

            assertThat(tab.status().holdsItsPlace()).isFalse();
        }

        @Test
        void shouldCloseWithItemsStillBeingPrepared() {
            Tab tab = closingTab(billing);
            pay(tab, "110.00", billing);

            tab.close(billing, WAITER, CLOSED_AT);

            assertThat(tab.items()).extracting(TabItem::status).containsOnly(TabItemStatus.PENDING);
            assertThat(tab.status()).isEqualTo(TabStatus.CLOSED);
        }

        @Test
        void shouldRefuseClosingWithBalanceDueAndStayClosing() {
            Tab tab = closingTab(billing);
            pay(tab, "109.99", billing);

            assertRejectedWith(() -> tab.close(billing, WAITER, CLOSED_AT), FOLIO_BALANCE_NOT_ZERO);
            assertThat(tab.status()).isEqualTo(TabStatus.CLOSING);
            assertThat(tab.destination()).isEmpty();
            assertThat(tab.closedAt()).isEmpty();
        }

        @Test
        void shouldRefuseClosingAnOpenTab() {
            Tab tab = tableTab();
            orderDish(tab, "10.00");

            assertRejectedWith(() -> tab.close(billing, WAITER, CLOSED_AT), TAB_NOT_CLOSING);
        }

        @Test
        void shouldRefuseClosingTwice() {
            Tab tab = closedTab(billing);

            assertRejectedWith(() -> tab.close(billing, WAITER, CLOSED_AT), TAB_NOT_CLOSING);
        }

        @Test
        void shouldRefuseEveryChangeOnceClosed() {
            Tab tab = closedTab(billing);

            assertRejectedWith(() -> tab.addItem(dish("5.00"), oneUnit(), WAITER, ORDERED_AT, FORTALEZA), TAB_NOT_OPEN);
            assertRejectedWith(tab::removeServiceCharge, TAB_NOT_OPEN);
            assertRejectedWith(() -> tab.startClosing(TEN_PERCENT, billing, WAITER, CLOSING_AT), TAB_NOT_OPEN);
            assertRejectedWith(() -> tab.cancel(CANCELLATION_REASON, billing, WAITER, CANCELLED_AT), TAB_NOT_OPEN);
        }
    }

    @Nested
    @DisplayName("cancelling a reopened tab")
    class CancellingAReopenedTab {

        private Tab reopenedWithoutActiveItems() {
            Tab tab = closingTab(billing);
            tab.reopen(REOPENING_REASON, billing);
            tab.cancelItem(tab.items().get(0).id(), "Cliente desistiu", WAITER, CANCELLED_AT);
            return tab;
        }

        @Test
        void shouldCloseTheFolioAlongWithTheTab() {
            Tab tab = reopenedWithoutActiveItems();

            tab.cancel(CANCELLATION_REASON, billing, WAITER, CANCELLED_AT);

            assertThat(tab.status()).isEqualTo(TabStatus.CANCELLED);
            assertThat(billing.closedFolios).containsExactly(tab.folioId().orElseThrow());
        }

        @Test
        void shouldRefuseCancellingWhileTheFolioHoldsACredit() {
            Tab tab = closingTab(billing);
            pay(tab, "10.00", billing);
            tab.reopen(REOPENING_REASON, billing);
            tab.cancelItem(tab.items().get(0).id(), "Cliente desistiu", WAITER, CANCELLED_AT);

            assertRejectedWith(() -> tab.cancel(CANCELLATION_REASON, billing, WAITER, CANCELLED_AT),
                    FOLIO_BALANCE_NOT_ZERO);
            assertThat(tab.status()).isEqualTo(TabStatus.OPEN);
            assertThat(tab.cancelledAt()).isEmpty();
        }

        @Test
        void shouldCheckTheActiveItemsBeforeClosingTheFolio() {
            Tab tab = closingTab(billing);
            tab.reopen(REOPENING_REASON, billing);

            assertRejectedWith(() -> tab.cancel(CANCELLATION_REASON, billing, WAITER, CANCELLED_AT), TAB_HAS_ACTIVE_ITEMS);
            assertThat(billing.closedFolios).isEmpty();
        }

        @Test
        void shouldCheckTheReasonBeforeClosingTheFolio() {
            Tab tab = reopenedWithoutActiveItems();

            assertRejectedWith(() -> tab.cancel("  ", billing, WAITER, CANCELLED_AT), INVALID_CANCELLATION_REASON);
            assertThat(billing.closedFolios).isEmpty();
        }

        @Test
        void shouldCancelATabWithoutFolioWithoutTouchingTheBilling() {
            Tab tab = tableTab();

            tab.cancel(CANCELLATION_REASON, billing, WAITER, CANCELLED_AT);

            assertThat(tab.status()).isEqualTo(TabStatus.CANCELLED);
            assertThat(billing.closedFolios).isEmpty();
        }
    }
}
