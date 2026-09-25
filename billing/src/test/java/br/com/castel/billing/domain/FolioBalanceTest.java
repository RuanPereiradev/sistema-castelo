package br.com.castel.billing.domain;

import static br.com.castel.billing.domain.FolioFixtures.adjust;
import static br.com.castel.billing.domain.FolioFixtures.consumption;
import static br.com.castel.billing.domain.FolioFixtures.money;
import static br.com.castel.billing.domain.FolioFixtures.pay;
import static br.com.castel.billing.domain.FolioFixtures.roomNight;
import static br.com.castel.billing.domain.FolioFixtures.stayFolio;
import static br.com.castel.billing.domain.FolioFixtures.tabFolio;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.sharedkernel.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** balance() = sum of every charge, with its sign, minus the sum of CONFIRMED payments (invariant 5). */
class FolioBalanceTest {

    @Nested
    @DisplayName("charges only")
    class ChargesOnly {

        @Test
        void shouldHaveZeroBalanceWhenJustOpened() {
            Folio folio = stayFolio();

            assertThat(folio.balance()).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldOweTheSumOfEveryCharge() {
            Folio folio = stayFolio();
            folio.post(roomNight("180.00"));
            folio.post(roomNight("180.00"));
            folio.post(consumption("47.50"));

            assertThat(folio.balance()).isEqualTo(money("407.50"));
        }

        @Test
        void shouldReportTotalChargesSeparatelyFromPayments() {
            Folio folio = stayFolio();
            folio.post(roomNight("180.00"));
            pay(folio, "80.00");

            assertThat(folio.totalCharges()).isEqualTo(money("180.00"));
            assertThat(folio.totalPayments()).isEqualTo(money("80.00"));
        }
    }

    @Nested
    @DisplayName("payments")
    class Payments {

        @Test
        void shouldOweTheRemainderAfterAPartialPayment() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));

            pay(folio, "50.00");

            assertThat(folio.balance()).isEqualTo(money("70.00"));
        }

        @Test
        void shouldSubtractPaymentsMadeWithDifferentMethods() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));

            pay(folio, PaymentMethod.PIX, "50.00");
            pay(folio, PaymentMethod.CASH, "30.00");
            pay(folio, PaymentMethod.CREDIT_CARD, "25.00");
            pay(folio, PaymentMethod.DEBIT_CARD, "15.00");

            assertThat(folio.balance()).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldGoNegativeOnAStayFolioPaidInAdvance() {
            Folio folio = stayFolio();

            pay(folio, "200.00");

            assertThat(folio.balance()).isEqualTo(money("-200.00"));
        }

        @Test
        void shouldGoNegativeOnAStayFolioPaidAboveWhatItOwes() {
            Folio folio = stayFolio();
            folio.post(roomNight("180.00"));

            pay(folio, "180.01");

            assertThat(folio.balance()).isEqualTo(money("-0.01"));
        }

        @Test
        void shouldStopSubtractingAPaymentOnceItIsRefunded() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));
            Payment pix = pay(folio, "50.00");
            pay(folio, PaymentMethod.CASH, "20.00");

            folio.refund(pix.id(), "Pix devolvido", FolioFixtures.ADMIN, FolioFixtures.LATER);

            assertThat(folio.balance()).isEqualTo(money("100.00"));
        }

        @Test
        void shouldRiseByExactlyTheRefundedAmount() {
            Folio folio = stayFolio();
            folio.post(roomNight("180.00"));
            Payment payment = pay(folio, "180.00");

            folio.refund(payment.id(), "Cobranca em duplicidade", FolioFixtures.ADMIN, FolioFixtures.LATER);

            assertThat(folio.balance()).isEqualTo(money("180.00"));
        }
    }

    @Nested
    @DisplayName("reversals and adjustments")
    class ReversalsAndAdjustments {

        @Test
        void shouldFallByExactlyTheReversedCharge() {
            Folio folio = stayFolio();
            folio.post(roomNight("180.00"));
            Charge wrongTab = folio.post(consumption("47.30"));

            folio.reverse(wrongTab.id(), "Comanda lancada no quarto errado");

            assertThat(folio.balance()).isEqualTo(money("180.00"));
        }

        @Test
        void shouldAddAPositiveAdjustment() {
            Folio folio = stayFolio();
            folio.post(roomNight("180.00"));

            adjust(folio, "15.00");

            assertThat(folio.balance()).isEqualTo(money("195.00"));
        }

        @Test
        void shouldSubtractANegativeAdjustment() {
            Folio folio = stayFolio();
            folio.post(roomNight("180.00"));

            adjust(folio, "-30.00");

            assertThat(folio.balance()).isEqualTo(money("150.00"));
        }

        @Test
        void shouldGoNegativeWhenAPaidStayChargeIsReversed() {
            Folio folio = stayFolio();
            Charge night = folio.post(roomNight("180.00"));
            pay(folio, "180.00");

            folio.reverse(night.id(), "Diaria cobrada em duplicidade");

            assertThat(folio.balance()).isEqualTo(money("-180.00"));
        }

        @Test
        void shouldCombineEveryKindOfEntry() {
            Folio folio = stayFolio();
            folio.post(roomNight("180.00"));
            folio.post(roomNight("180.00"));
            Charge tab = folio.post(consumption("62.40"));
            folio.reverse(tab.id(), "Comanda de outro hospede");
            adjust(folio, "-18.00");
            Payment deposit = pay(folio, "100.00");
            pay(folio, PaymentMethod.CASH, "42.00");
            folio.refund(deposit.id(), "Sinal devolvido", FolioFixtures.ADMIN, FolioFixtures.LATER);

            assertThat(folio.balance()).isEqualTo(money("300.00"));
        }
    }

    @Nested
    @DisplayName("cents")
    class Cents {

        @Test
        void shouldAddCentsWithoutBinaryRounding() {
            Folio folio = tabFolio();
            folio.post(consumption("0.10"));
            folio.post(consumption("0.20"));

            assertThat(folio.balance()).isEqualTo(money("0.30"));
        }

        @Test
        void shouldOweOneCent() {
            Folio folio = tabFolio();
            folio.post(consumption("100.00"));

            pay(folio, "99.99");

            assertThat(folio.balance()).isEqualTo(money("0.01"));
        }

        @Test
        void shouldSettleAOneCentCharge() {
            Folio folio = tabFolio();
            folio.post(consumption("0.01"));

            pay(folio, "0.01");

            assertThat(folio.balance()).isEqualTo(Money.ZERO);
        }
    }
}
