package br.com.castel.billing.domain;

import static br.com.castel.billing.domain.FolioFixtures.ADMIN;
import static br.com.castel.billing.domain.FolioFixtures.LATER;
import static br.com.castel.billing.domain.FolioFixtures.OPENED_AT;
import static br.com.castel.billing.domain.FolioFixtures.OPERATOR;
import static br.com.castel.billing.domain.FolioFixtures.PROPERTY_ID;
import static br.com.castel.billing.domain.FolioFixtures.adjust;
import static br.com.castel.billing.domain.FolioFixtures.assertRejectedWith;
import static br.com.castel.billing.domain.FolioFixtures.closedTabFolio;
import static br.com.castel.billing.domain.FolioFixtures.consumption;
import static br.com.castel.billing.domain.FolioFixtures.money;
import static br.com.castel.billing.domain.FolioFixtures.pay;
import static br.com.castel.billing.domain.FolioFixtures.roomNight;
import static br.com.castel.billing.domain.FolioFixtures.stayFolio;
import static br.com.castel.billing.domain.FolioFixtures.tabFolio;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.billing.api.FolioOwner;
import br.com.castel.billing.api.FolioReference;
import br.com.castel.billing.api.FolioStatus;
import br.com.castel.billing.api.FolioType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Opening, reference, closing and the closed folio (invariants 1, 2, 4, 6, 7, 8 and 10 of task 1.3). One
 * folio per owner (3) and a reference code unique among open stays (the rest of 8) are set rules, checked
 * by the use case, and are not covered here.
 */
class FolioLifecycleTest {

    private static final String INVALID_FOLIO_REFERENCE = "INVALID_FOLIO_REFERENCE";
    private static final String FOLIO_CLOSED = "FOLIO_CLOSED";
    private static final String FOLIO_BALANCE_NOT_ZERO = "FOLIO_BALANCE_NOT_ZERO";

    private static Folio stayFolioWith(FolioReference reference) {
        return Folio.openForStay(PROPERTY_ID, FolioOwner.reservation(UUID.randomUUID()), reference, OPENED_AT);
    }

    @Nested
    @DisplayName("opening (invariants 1 and 2)")
    class Opening {

        @Test
        void shouldOpenAStayFolioForAReservationWithItsReference() {
            FolioOwner reservation = FolioOwner.reservation(UUID.randomUUID());
            FolioReference reference = new FolioReference("102", "Quarto 102 - Silva");

            Folio folio = Folio.openForStay(PROPERTY_ID, reservation, reference, OPENED_AT);

            assertThat(folio.type()).isEqualTo(FolioType.STAY);
            assertThat(folio.status()).isEqualTo(FolioStatus.OPEN);
            assertThat(folio.owner()).isEqualTo(reservation);
            assertThat(folio.reference()).contains(reference);
            assertThat(folio.propertyId()).isEqualTo(PROPERTY_ID);
            assertThat(folio.openedAt()).isEqualTo(OPENED_AT);
            assertThat(folio.closedAt()).isEmpty();
        }

        @Test
        void shouldOpenATabFolioWithoutReference() {
            FolioOwner tab = FolioOwner.tab(UUID.randomUUID());

            Folio folio = Folio.openForTab(PROPERTY_ID, tab, OPENED_AT);

            assertThat(folio.type()).isEqualTo(FolioType.TAB);
            assertThat(folio.status()).isEqualTo(FolioStatus.OPEN);
            assertThat(folio.owner()).isEqualTo(tab);
            assertThat(folio.reference()).isEmpty();
        }

        @Test
        void shouldRejectAStayFolioOwnedByATab() {
            FolioOwner tab = FolioOwner.tab(UUID.randomUUID());
            FolioReference reference = new FolioReference("102", "Quarto 102");

            assertThatThrownBy(() -> Folio.openForStay(PROPERTY_ID, tab, reference, OPENED_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectATabFolioOwnedByAReservation() {
            FolioOwner reservation = FolioOwner.reservation(UUID.randomUUID());

            assertThatThrownBy(() -> Folio.openForTab(PROPERTY_ID, reservation, OPENED_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectAStayFolioWithoutReference() {
            FolioOwner reservation = FolioOwner.reservation(UUID.randomUUID());

            assertThatThrownBy(() -> Folio.openForStay(PROPERTY_ID, reservation, null, OPENED_AT));
        }
    }

    @Nested
    @DisplayName("reference (invariant 4)")
    class Reference {

        @Test
        void shouldTrimCodeAndLabel() {
            Folio folio = stayFolioWith(new FolioReference("  102  ", "  Quarto 102  "));

            assertThat(folio.reference()).contains(new FolioReference("102", "Quarto 102"));
        }

        @Test
        void shouldRejectABlankCode() {
            assertRejectedWith(() -> stayFolioWith(new FolioReference("  ", "Quarto 102")), INVALID_FOLIO_REFERENCE);
        }

        @Test
        void shouldAcceptACodeOfExactly20Characters() {
            Folio folio = stayFolioWith(new FolioReference("c".repeat(20), "Quarto"));

            assertThat(folio.reference()).hasValueSatisfying(reference -> assertThat(reference.code()).hasSize(20));
        }

        @Test
        void shouldRejectACodeLongerThan20Characters() {
            assertRejectedWith(() -> stayFolioWith(new FolioReference("c".repeat(21), "Quarto")), INVALID_FOLIO_REFERENCE);
        }

        @Test
        void shouldRejectABlankLabel() {
            assertRejectedWith(() -> stayFolioWith(new FolioReference("102", " ")), INVALID_FOLIO_REFERENCE);
        }

        @Test
        void shouldAcceptALabelOfExactly150Characters() {
            Folio folio = stayFolioWith(new FolioReference("102", "l".repeat(150)));

            assertThat(folio.reference()).hasValueSatisfying(reference -> assertThat(reference.label()).hasSize(150));
        }

        @Test
        void shouldRejectALabelLongerThan150Characters() {
            assertRejectedWith(() -> stayFolioWith(new FolioReference("102", "l".repeat(151))), INVALID_FOLIO_REFERENCE);
        }

        @Test
        void shouldPointAStayFolioAtTheNewRoomKeepingItsCharges() {
            Folio folio = stayFolio();
            Charge night = folio.post(roomNight("180.00"));
            FolioReference newRoom = new FolioReference("205", "Quarto 205 - Silva");

            folio.changeReference(newRoom);

            assertThat(folio.reference()).contains(newRoom);
            assertThat(folio.charges()).containsExactly(night);
        }

        @Test
        void shouldRejectAnInvalidNewReference() {
            Folio folio = stayFolio();

            assertRejectedWith(
                    () -> folio.changeReference(new FolioReference("c".repeat(21), "Quarto")), INVALID_FOLIO_REFERENCE);
        }

        @Test
        void shouldRefuseAReferenceOnATabFolio() {
            Folio folio = tabFolio();
            FolioReference reference = new FolioReference("102", "Quarto 102");

            assertThatThrownBy(() -> folio.changeReference(reference)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("close (invariants 7 and 10)")
    class Close {

        @Test
        void shouldCloseAFolioWhoseBalanceIsExactlyZero() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));
            pay(folio, "120.00");

            folio.close(OPERATOR, LATER);

            assertThat(folio.status()).isEqualTo(FolioStatus.CLOSED);
            assertThat(folio.closedAt()).contains(LATER);
        }

        @Test
        void shouldCloseAFolioThatNeverHadACharge() {
            Folio folio = stayFolio();

            folio.close(OPERATOR, LATER);

            assertThat(folio.status()).isEqualTo(FolioStatus.CLOSED);
        }

        @Test
        void shouldRefuseToCloseWhileOneCentIsOwed() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));
            pay(folio, "119.99");

            assertRejectedWith(() -> folio.close(OPERATOR, LATER), FOLIO_BALANCE_NOT_ZERO);
        }

        @Test
        void shouldRefuseToCloseWhileTheGuestHasOneCentOfCredit() {
            Folio folio = stayFolio();
            folio.post(roomNight("180.00"));
            pay(folio, "180.01");

            assertRejectedWith(() -> folio.close(OPERATOR, LATER), FOLIO_BALANCE_NOT_ZERO);
        }

        @Test
        void shouldStayOpenWhenTheCloseIsRefused() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));

            assertThatThrownBy(() -> folio.close(OPERATOR, LATER));

            assertThat(folio.status()).isEqualTo(FolioStatus.OPEN);
            assertThat(folio.closedAt()).isEmpty();
        }

        @Test
        void shouldCloseOnceAnAdjustmentSettlesTheDifference() {
            Folio folio = stayFolio();
            folio.post(roomNight("180.00"));
            pay(folio, "179.99");
            adjust(folio, "-0.01");

            folio.close(OPERATOR, LATER);

            assertThat(folio.status()).isEqualTo(FolioStatus.CLOSED);
        }

        @Test
        void shouldNotCloseByItselfWhenTheBalanceReachesZero() {
            Folio folio = tabFolio();
            folio.post(consumption("120.00"));

            pay(folio, "120.00");

            assertThat(folio.status()).isEqualTo(FolioStatus.OPEN);
        }
    }

    @Nested
    @DisplayName("closed folio refuses every write (invariant 6)")
    class ClosedFolio {

        @Test
        void shouldRejectAChargeOnAClosedFolio() {
            Folio folio = closedTabFolio();

            assertRejectedWith(() -> folio.post(consumption("10.00")), FOLIO_CLOSED);
        }

        @Test
        void shouldRejectAnAdjustmentOnAClosedFolio() {
            Folio folio = closedTabFolio();

            assertRejectedWith(() -> adjust(folio, "-10.00"), FOLIO_CLOSED);
        }

        @Test
        void shouldRejectAReversalOnAClosedFolio() {
            Folio folio = closedTabFolio();
            Charge charge = folio.charges().getFirst();

            assertRejectedWith(() -> folio.reverse(charge.id(), "Tarde demais"), FOLIO_CLOSED);
        }

        @Test
        void shouldRejectANewPaymentOnAClosedFolio() {
            Folio folio = stayFolio();
            folio.close(OPERATOR, LATER);

            assertRejectedWith(() -> pay(folio, "10.00"), FOLIO_CLOSED);
        }

        @Test
        void shouldRejectARefundOnAClosedFolio() {
            Folio folio = closedTabFolio();
            Payment payment = folio.payments().getFirst();

            assertRejectedWith(() -> folio.refund(payment.id(), "Tarde demais", ADMIN, LATER), FOLIO_CLOSED);
        }

        @Test
        void shouldRejectAReferenceChangeOnAClosedFolio() {
            Folio folio = stayFolio();
            folio.close(OPERATOR, LATER);

            assertRejectedWith(() -> folio.changeReference(new FolioReference("205", "Quarto 205")), FOLIO_CLOSED);
        }

        @Test
        void shouldRejectClosingTwice() {
            Folio folio = closedTabFolio();

            assertRejectedWith(() -> folio.close(OPERATOR, LATER.plusSeconds(60)), FOLIO_CLOSED);
        }

        @Test
        void shouldKeepTheClosedFolioIntactAfterRefusingAWrite() {
            Folio folio = closedTabFolio();

            assertThatThrownBy(() -> folio.post(consumption("10.00")));

            assertThat(folio.charges()).hasSize(1);
            assertThat(folio.balance()).isEqualTo(money("0.00"));
            assertThat(folio.closedAt()).contains(LATER);
        }
    }
}
