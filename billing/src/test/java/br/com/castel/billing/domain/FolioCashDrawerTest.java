package br.com.castel.billing.domain;

import static br.com.castel.billing.domain.FolioFixtures.LATER;
import static br.com.castel.billing.domain.FolioFixtures.OPERATOR;
import static br.com.castel.billing.domain.FolioFixtures.assertRejectedWith;
import static br.com.castel.billing.domain.FolioFixtures.closedTabFolio;
import static br.com.castel.billing.domain.FolioFixtures.consumption;
import static br.com.castel.billing.domain.FolioFixtures.money;
import static br.com.castel.billing.domain.FolioFixtures.pay;
import static br.com.castel.billing.domain.FolioFixtures.tabFolio;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.billing.api.PaymentMethod;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Folio.receive with a cash drawer (invariants 21 to 23 of task 2.4). */
class FolioCashDrawerTest {

    private static final String CASH_DRAWER_SESSION_NOT_OPEN = "CASH_DRAWER_SESSION_NOT_OPEN";
    private static final String FOLIO_CLOSED = "FOLIO_CLOSED";
    private static final String PAYMENT_EXCEEDS_BALANCE = "PAYMENT_EXCEEDS_BALANCE";
    private static final String KEY = "cash-3c9e";

    private static final CashDrawerSessionId SESSION = CashDrawerSessionId.newId();
    private static final CashDrawerAssignment OPEN = CashDrawerAssignment.of(Optional.of(SESSION), false);
    private static final CashDrawerAssignment REQUIRED_WITHOUT_SESSION =
            CashDrawerAssignment.of(Optional.empty(), true);

    private static Folio tabWith(String consumption) {
        Folio folio = tabFolio();
        folio.post(consumption(consumption));
        return folio;
    }

    @Test
    void shouldCarryTheOpenSessionOnACashPayment() {
        Folio folio = tabWith("150.00");

        Payment payment = folio.receive(PaymentMethod.CASH, money("150.00"), KEY, OPERATOR, LATER, OPEN);

        assertThat(payment.cashDrawerSessionId()).contains(SESSION);
        assertThat(folio.cashDrawerSessionOf(payment.id())).contains(SESSION);
    }

    @Test
    void shouldNeverCarryASessionOnAPixPayment() {
        Folio folio = tabWith("150.00");

        Payment payment = folio.receive(PaymentMethod.PIX, money("150.00"), KEY, OPERATOR, LATER, OPEN);

        assertThat(payment.cashDrawerSessionId()).isEmpty();
        assertThat(folio.cashDrawerSessionOf(payment.id())).isEmpty();
    }

    @Test
    void shouldLeaveCashUnlinkedThroughTheFiveArgumentOverload() {
        Folio folio = tabWith("150.00");

        Payment payment = pay(folio, PaymentMethod.CASH, "150.00");

        assertThat(payment.cashDrawerSessionId()).isEmpty();
    }

    @Test
    void shouldRejectCashWhenTheControlIsOnAndNoSessionIsOpen() {
        Folio folio = tabWith("150.00");

        assertRejectedWith(
                () -> folio.receive(
                        PaymentMethod.CASH, money("150.00"), KEY, OPERATOR, LATER, REQUIRED_WITHOUT_SESSION),
                CASH_DRAWER_SESSION_NOT_OPEN);
        assertThat(folio.payments()).isEmpty();
        assertThat(folio.balance()).isEqualTo(money("150.00"));
    }

    @Test
    void shouldReplayARegisteredCashPaymentBeforeCheckingTheDrawer() {
        Folio folio = tabWith("150.00");
        Payment original = folio.receive(PaymentMethod.CASH, money("150.00"), KEY, OPERATOR, LATER, OPEN);

        Payment replay = folio.receive(
                PaymentMethod.CASH, money("150.00"), KEY, OPERATOR, LATER, REQUIRED_WITHOUT_SESSION);

        assertThat(replay).isSameAs(original);
        assertThat(replay.cashDrawerSessionId()).contains(SESSION);
    }

    @Test
    void shouldReportTheClosedFolioBeforeTheDrawer() {
        Folio folio = closedTabFolio();

        assertRejectedWith(
                () -> folio.receive(
                        PaymentMethod.CASH, money("10.00"), KEY, OPERATOR, LATER, REQUIRED_WITHOUT_SESSION),
                FOLIO_CLOSED);
    }

    @Test
    void shouldReportTheBalanceBeforeTheDrawer() {
        Folio folio = tabWith("150.00");

        assertRejectedWith(
                () -> folio.receive(
                        PaymentMethod.CASH, money("150.01"), KEY, OPERATOR, LATER, REQUIRED_WITHOUT_SESSION),
                PAYMENT_EXCEEDS_BALANCE);
    }
}
