package br.com.castel.billing.domain;

import static br.com.castel.billing.domain.FolioFixtures.ADMIN;
import static br.com.castel.billing.domain.FolioFixtures.LATER;
import static br.com.castel.billing.domain.FolioFixtures.OPERATOR;
import static br.com.castel.billing.domain.FolioFixtures.assertRejectedWith;
import static br.com.castel.billing.domain.FolioFixtures.consumption;
import static br.com.castel.billing.domain.FolioFixtures.money;
import static br.com.castel.billing.domain.FolioFixtures.pay;
import static br.com.castel.billing.domain.FolioFixtures.roomNight;
import static br.com.castel.billing.domain.FolioFixtures.stayFolio;
import static br.com.castel.billing.domain.FolioFixtures.tabFolio;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.sharedkernel.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Receiving, replaying and refunding payments (invariants 19 to 24 of task 1.3). */
class FolioPaymentTest {

    private static final String INVALID_PAYMENT_AMOUNT = "INVALID_PAYMENT_AMOUNT";
    private static final String PAYMENT_METHOD_NOT_ACCEPTED = "PAYMENT_METHOD_NOT_ACCEPTED";
    private static final String PAYMENT_EXCEEDS_BALANCE = "PAYMENT_EXCEEDS_BALANCE";
    private static final String INVALID_IDEMPOTENCY_KEY = "INVALID_IDEMPOTENCY_KEY";
    private static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    private static final String PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND";
    private static final String PAYMENT_ALREADY_REFUNDED = "PAYMENT_ALREADY_REFUNDED";
    private static final String INVALID_PAYMENT_REFUND_REASON = "INVALID_PAYMENT_REFUND_REASON";

    private static final String KEY = "9b1f0c52-front-retry";

    @Nested
    @DisplayName("receive (invariants 19, 20 and 22)")
    class Receive {

        @Test
        void shouldBeBornConfirmedWithTheGivenPaidAt() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));

            Payment payment = folio.receive(PaymentMethod.PIX, money("50.00"), KEY, OPERATOR, LATER);

            assertThat(payment.status()).isEqualTo(PaymentStatus.CONFIRMED);
            assertThat(payment.isConfirmed()).isTrue();
            assertThat(payment.paidAt()).isEqualTo(LATER);
            assertThat(folio.payments()).containsExactly(payment);
        }

        @Test
        void shouldRejectAPaymentOfZero() {
            Folio folio = stayFolio();

            assertRejectedWith(() -> pay(folio, "0.00"), INVALID_PAYMENT_AMOUNT);
        }

        @Test
        void shouldRejectANegativePayment() {
            Folio folio = stayFolio();

            assertRejectedWith(() -> pay(folio, "-0.01"), INVALID_PAYMENT_AMOUNT);
        }

        @Test
        void shouldRejectRoomAccountAsAManualPayment() {
            Folio folio = stayFolio();
            folio.post(roomNight("180.00"));

            assertRejectedWith(() -> pay(folio, PaymentMethod.ROOM_ACCOUNT, "180.00"), PAYMENT_METHOD_NOT_ACCEPTED);
        }

        @Test
        void shouldAcceptEveryOtherMethodOnATabFolio() {
            Folio folio = tabFolio();
            folio.post(consumption("40.00"));

            pay(folio, PaymentMethod.CASH, "10.00");
            pay(folio, PaymentMethod.PIX, "10.00");
            pay(folio, PaymentMethod.CREDIT_CARD, "10.00");
            pay(folio, PaymentMethod.DEBIT_CARD, "10.00");

            assertThat(folio.payments()).hasSize(4);
        }

        @Test
        void shouldListPaymentsInTheOrderTheyWereReceived() {
            Folio folio = tabFolio();
            folio.post(consumption("30.00"));
            Payment first = pay(folio, PaymentMethod.CASH, "10.00");
            Payment second = pay(folio, PaymentMethod.PIX, "20.00");

            assertThat(folio.payments()).containsExactly(first, second);
        }

        @Test
        void shouldNotLetThePaymentListBeChangedFromOutside() {
            Folio folio = stayFolio();
            Payment payment = pay(folio, "10.00");

            assertThatThrownBy(() -> folio.payments().remove(payment))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("payment above the balance (invariant 21)")
    class AboveBalance {

        @Test
        void shouldAcceptOnATabFolioAPaymentOfExactlyTheBalance() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));

            pay(folio, "120.00");

            assertThat(folio.balance()).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldRejectOnATabFolioAPaymentOneCentAboveTheBalance() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));

            assertRejectedWith(() -> pay(folio, "120.01"), PAYMENT_EXCEEDS_BALANCE);
        }

        @Test
        void shouldCompareAgainstWhatIsLeftAfterEarlierPayments() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));
            pay(folio, "50.00");

            assertRejectedWith(() -> pay(folio, "70.01"), PAYMENT_EXCEEDS_BALANCE);
        }

        @Test
        void shouldRejectAnyPaymentOnATabFolioThatOwesNothing() {
            Folio folio = tabFolio();

            assertRejectedWith(() -> pay(folio, "0.01"), PAYMENT_EXCEEDS_BALANCE);
        }

        @Test
        void shouldLeaveTheTabFolioUntouchedWhenTheExcessIsRefused() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));

            assertThatThrownBy(() -> pay(folio, "120.01"));

            assertThat(folio.payments()).isEmpty();
            assertThat(folio.balance()).isEqualTo(money("120.00"));
        }

        @Test
        void shouldAcceptOnAStayFolioAPaymentAboveTheBalance() {
            Folio folio = stayFolio();
            folio.post(roomNight("180.00"));

            pay(folio, "500.00");

            assertThat(folio.balance()).isEqualTo(money("-320.00"));
        }
    }

    @Nested
    @DisplayName("idempotency (invariant 23)")
    class Idempotency {

        @Test
        void shouldReturnTheExistingPaymentWhenReplayedWithTheSameMethodAndAmount() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));
            Payment original = folio.receive(PaymentMethod.PIX, money("50.00"), KEY, OPERATOR, LATER);

            Payment replay = folio.receive(PaymentMethod.PIX, money("50.00"), KEY, OPERATOR, LATER.plusSeconds(5));

            assertThat(replay).isSameAs(original);
            assertThat(folio.payments()).hasSize(1);
            assertThat(folio.balance()).isEqualTo(money("70.00"));
        }

        @Test
        void shouldReplayBeforeCheckingTheBalanceOfATabFolio() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));
            Payment original = folio.receive(PaymentMethod.PIX, money("120.00"), KEY, OPERATOR, LATER);

            Payment replay = folio.receive(PaymentMethod.PIX, money("120.00"), KEY, OPERATOR, LATER);

            assertThat(replay).isSameAs(original);
            assertThat(folio.balance()).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldReplayEvenAfterTheFolioIsClosed() {
            Folio folio = tabFolio();
            folio.post(consumption("50.00"));
            Payment original = folio.receive(PaymentMethod.CASH, money("50.00"), KEY, OPERATOR, LATER);
            folio.close(OPERATOR, LATER);

            Payment replay = folio.receive(PaymentMethod.CASH, money("50.00"), KEY, OPERATOR, LATER);

            assertThat(replay).isSameAs(original);
        }

        @Test
        void shouldReplayAPaymentAlreadyRefunded() {
            Folio folio = stayFolio();
            Payment original = folio.receive(PaymentMethod.PIX, money("50.00"), KEY, OPERATOR, LATER);
            folio.refund(original.id(), "Pix devolvido", ADMIN, LATER);

            Payment replay = folio.receive(PaymentMethod.PIX, money("50.00"), KEY, OPERATOR, LATER);

            assertThat(replay).isSameAs(original);
            assertThat(replay.status()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(folio.payments()).hasSize(1);
        }

        @Test
        void shouldRejectTheSameKeyWithDifferentDataAsReusedEvenOnAClosedFolio() {
            Folio folio = tabFolio();
            folio.post(consumption("50.00"));
            folio.receive(PaymentMethod.CASH, money("50.00"), KEY, OPERATOR, LATER);
            folio.close(OPERATOR, LATER);

            assertRejectedWith(
                    () -> folio.receive(PaymentMethod.CASH, money("40.00"), KEY, OPERATOR, LATER),
                    IDEMPOTENCY_KEY_REUSED);
        }

        @Test
        void shouldRejectTheSameKeyWithADifferentAmount() {
            Folio folio = stayFolio();
            folio.receive(PaymentMethod.PIX, money("50.00"), KEY, OPERATOR, LATER);

            assertRejectedWith(
                    () -> folio.receive(PaymentMethod.PIX, money("50.01"), KEY, OPERATOR, LATER),
                    IDEMPOTENCY_KEY_REUSED);
        }

        @Test
        void shouldRejectTheSameKeyWithADifferentMethod() {
            Folio folio = stayFolio();
            folio.receive(PaymentMethod.PIX, money("50.00"), KEY, OPERATOR, LATER);

            assertRejectedWith(
                    () -> folio.receive(PaymentMethod.CASH, money("50.00"), KEY, OPERATOR, LATER),
                    IDEMPOTENCY_KEY_REUSED);
        }

        @Test
        void shouldFindThePaymentByItsKey() {
            Folio folio = stayFolio();
            Payment payment = folio.receive(PaymentMethod.PIX, money("50.00"), KEY, OPERATOR, LATER);

            assertThat(folio.paymentWithKey(KEY)).containsSame(payment);
            assertThat(folio.paymentWithKey("another-key")).isEmpty();
        }

        @Test
        void shouldTrimTheKey() {
            Folio folio = stayFolio();

            Payment payment = folio.receive(PaymentMethod.PIX, money("50.00"), "  " + KEY + "  ", OPERATOR, LATER);

            assertThat(payment.idempotencyKey()).isEqualTo(KEY);
        }

        @Test
        void shouldRejectAMissingKey() {
            Folio folio = stayFolio();

            assertRejectedWith(
                    () -> folio.receive(PaymentMethod.PIX, money("50.00"), null, OPERATOR, LATER),
                    INVALID_IDEMPOTENCY_KEY);
        }

        @Test
        void shouldRejectABlankKey() {
            Folio folio = stayFolio();

            assertRejectedWith(
                    () -> folio.receive(PaymentMethod.PIX, money("50.00"), "   ", OPERATOR, LATER),
                    INVALID_IDEMPOTENCY_KEY);
        }

        @Test
        void shouldAcceptAKeyOfExactly100Characters() {
            Folio folio = stayFolio();

            Payment payment = folio.receive(PaymentMethod.PIX, money("50.00"), "k".repeat(100), OPERATOR, LATER);

            assertThat(payment.idempotencyKey()).hasSize(100);
        }

        @Test
        void shouldRejectAKeyLongerThan100Characters() {
            Folio folio = stayFolio();

            assertRejectedWith(
                    () -> folio.receive(PaymentMethod.PIX, money("50.00"), "k".repeat(101), OPERATOR, LATER),
                    INVALID_IDEMPOTENCY_KEY);
        }
    }

    @Nested
    @DisplayName("refund (invariant 24)")
    class Refund {

        @Test
        void shouldMoveTheRefundedPaymentToRefunded() {
            Folio folio = stayFolio();
            Payment payment = pay(folio, "50.00");

            folio.refund(payment.id(), "Pix devolvido", ADMIN, LATER);

            assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(payment.isConfirmed()).isFalse();
            assertThat(folio.payments()).containsExactly(payment);
        }

        @Test
        void shouldRejectRefundingTheSamePaymentTwice() {
            Folio folio = stayFolio();
            Payment payment = pay(folio, "50.00");
            folio.refund(payment.id(), "Pix devolvido", ADMIN, LATER);

            assertRejectedWith(
                    () -> folio.refund(payment.id(), "De novo", ADMIN, LATER), PAYMENT_ALREADY_REFUNDED);
        }

        @Test
        void shouldRejectRefundingAPaymentOfAnotherFolio() {
            Folio folio = stayFolio();
            Folio otherFolio = stayFolio();
            Payment otherPayment = pay(otherFolio, "50.00");

            assertRejectedWith(
                    () -> folio.refund(otherPayment.id(), "Engano", ADMIN, LATER), PAYMENT_NOT_FOUND);
        }

        @Test
        void shouldRejectARefundWithoutReason() {
            Folio folio = stayFolio();
            Payment payment = pay(folio, "50.00");

            assertRejectedWith(
                    () -> folio.refund(payment.id(), "  ", ADMIN, LATER), INVALID_PAYMENT_REFUND_REASON);
        }

        @Test
        void shouldRejectARefundReasonLongerThan500Characters() {
            Folio folio = stayFolio();
            Payment payment = pay(folio, "50.00");

            assertRejectedWith(
                    () -> folio.refund(payment.id(), "x".repeat(501), ADMIN, LATER), INVALID_PAYMENT_REFUND_REASON);
        }

        @Test
        void shouldKeepThePaymentConfirmedWhenTheRefundIsRefused() {
            Folio folio = stayFolio();
            Payment payment = pay(folio, "50.00");

            assertThatThrownBy(() -> folio.refund(payment.id(), "", ADMIN, LATER));

            assertThat(payment.isConfirmed()).isTrue();
            assertThat(folio.balance()).isEqualTo(money("-50.00"));
        }
    }
}
