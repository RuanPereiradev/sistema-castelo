package br.com.castel.billing.domain;

import static br.com.castel.billing.domain.FolioFixtures.ADMIN;
import static br.com.castel.billing.domain.FolioFixtures.adjust;
import static br.com.castel.billing.domain.FolioFixtures.assertRejectedWith;
import static br.com.castel.billing.domain.FolioFixtures.consumption;
import static br.com.castel.billing.domain.FolioFixtures.money;
import static br.com.castel.billing.domain.FolioFixtures.roomNight;
import static br.com.castel.billing.domain.FolioFixtures.stayFolio;
import static br.com.castel.billing.domain.FolioFixtures.tabFolio;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.sharedkernel.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Posting, adjustment and reversal of charges (invariants 9 and 11 to 18 of task 1.3). */
class FolioChargeTest {

    private static final String INVALID_CHARGE_AMOUNT = "INVALID_CHARGE_AMOUNT";
    private static final String INVALID_CHARGE_DESCRIPTION = "INVALID_CHARGE_DESCRIPTION";
    private static final String INVALID_CHARGE_REASON = "INVALID_CHARGE_REASON";
    private static final String CHARGE_TYPE_NOT_ACCEPTED = "CHARGE_TYPE_NOT_ACCEPTED";
    private static final String CHARGE_NOT_FOUND = "CHARGE_NOT_FOUND";
    private static final String CHARGE_NOT_REVERSIBLE = "CHARGE_NOT_REVERSIBLE";
    private static final String CHARGE_ALREADY_REVERSED = "CHARGE_ALREADY_REVERSED";

    @Nested
    @DisplayName("post (invariants 11 and 12)")
    class Post {

        @Test
        void shouldPostARoomNightAsARoomNightChargeOnAStayFolio() {
            Folio folio = stayFolio();

            Charge charge = folio.post(roomNight("180.00"));

            assertThat(charge).isInstanceOf(RoomNightCharge.class);
            assertThat(folio.charges()).containsExactly(charge);
        }

        @Test
        void shouldPostConsumptionAsATabChargeOnATabFolio() {
            Folio folio = tabFolio();

            Charge charge = folio.post(consumption("47.30"));

            assertThat(charge).isInstanceOf(TabCharge.class);
        }

        @Test
        void shouldRejectARoomNightOnATabFolio() {
            Folio folio = tabFolio();

            assertRejectedWith(() -> folio.post(roomNight("180.00")), CHARGE_TYPE_NOT_ACCEPTED);
        }

        @Test
        void shouldRejectAChargeOfZero() {
            Folio folio = tabFolio();

            assertRejectedWith(() -> folio.post(consumption("0.00")), INVALID_CHARGE_AMOUNT);
        }

        @Test
        void shouldRejectANegativeCharge() {
            Folio folio = stayFolio();

            assertRejectedWith(() -> folio.post(roomNight("-0.01")), INVALID_CHARGE_AMOUNT);
        }

        @Test
        void shouldLeaveTheFolioUntouchedWhenAChargeIsRefused() {
            Folio folio = tabFolio();

            assertThatThrownBy(() -> folio.post(consumption("0.00")));

            assertThat(folio.charges()).isEmpty();
            assertThat(folio.balance()).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldTrimTheDescription() {
            Folio folio = tabFolio();

            Charge charge = folio.post(consumption("10.00", "  Comanda 17  "));

            assertThat(charge.description()).isEqualTo("Comanda 17");
        }

        @Test
        void shouldRejectABlankDescription() {
            Folio folio = tabFolio();

            assertRejectedWith(() -> folio.post(consumption("10.00", "   ")), INVALID_CHARGE_DESCRIPTION);
        }

        @Test
        void shouldAcceptADescriptionOfExactly255Characters() {
            Folio folio = tabFolio();

            Charge charge = folio.post(consumption("10.00", "x".repeat(255)));

            assertThat(charge.description()).hasSize(255);
        }

        @Test
        void shouldRejectADescriptionLongerThan255Characters() {
            Folio folio = tabFolio();

            assertRejectedWith(() -> folio.post(consumption("10.00", "x".repeat(256))), INVALID_CHARGE_DESCRIPTION);
        }
    }

    @Nested
    @DisplayName("charges() (invariants 9 and 14)")
    class ChargeList {

        @Test
        void shouldListChargesInTheOrderTheyWerePosted() {
            Folio folio = stayFolio();
            Charge first = folio.post(roomNight("180.00"));
            Charge second = folio.post(consumption("47.30"));
            Charge third = adjust(folio, "-10.00");

            assertThat(folio.charges()).containsExactly(first, second, third);
        }

        @Test
        void shouldNotLetTheChargeListBeChangedFromOutside() {
            Folio folio = stayFolio();
            Charge night = folio.post(roomNight("180.00"));

            assertThatThrownBy(() -> folio.charges().remove(night)).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("postAdjustment (invariant 13)")
    class Adjustment {

        @Test
        void shouldRecordWhoAuthorisedTheAdjustmentAndWhy() {
            Folio folio = stayFolio();

            AdjustmentCharge adjustment =
                    folio.postAdjustment(money("-20.00"), "Desconto", "Chuveiro quebrado", ADMIN);

            assertThat(adjustment.authorizedBy()).contains(ADMIN);
            assertThat(adjustment.reason()).contains("Chuveiro quebrado");
            assertThat(adjustment.amount()).isEqualTo(money("-20.00"));
        }

        @Test
        void shouldAcceptANegativeAdjustmentOfOneCent() {
            Folio folio = stayFolio();

            AdjustmentCharge adjustment = adjust(folio, "-0.01");

            assertThat(adjustment.amount()).isEqualTo(money("-0.01"));
        }

        @Test
        void shouldRejectAnAdjustmentOfZero() {
            Folio folio = stayFolio();

            assertRejectedWith(() -> adjust(folio, "0.00"), INVALID_CHARGE_AMOUNT);
        }

        @Test
        void shouldRejectABlankAdjustmentDescription() {
            Folio folio = stayFolio();

            assertRejectedWith(
                    () -> folio.postAdjustment(money("-5.00"), " ", "Motivo", ADMIN), INVALID_CHARGE_DESCRIPTION);
        }

        @Test
        void shouldRejectABlankAdjustmentReason() {
            Folio folio = stayFolio();

            assertRejectedWith(
                    () -> folio.postAdjustment(money("-5.00"), "Desconto", "  ", ADMIN), INVALID_CHARGE_REASON);
        }

        @Test
        void shouldAcceptAnAdjustmentReasonOfExactly500Characters() {
            Folio folio = stayFolio();

            AdjustmentCharge adjustment = folio.postAdjustment(money("-5.00"), "Desconto", "x".repeat(500), ADMIN);

            assertThat(adjustment.reason()).hasValueSatisfying(reason -> assertThat(reason).hasSize(500));
        }

        @Test
        void shouldRejectAnAdjustmentReasonLongerThan500Characters() {
            Folio folio = stayFolio();

            assertRejectedWith(
                    () -> folio.postAdjustment(money("-5.00"), "Desconto", "x".repeat(501), ADMIN),
                    INVALID_CHARGE_REASON);
        }
    }

    @Nested
    @DisplayName("reverse (invariants 15 to 18)")
    class Reverse {

        @Test
        void shouldWriteAnOpposingChargeOfTheSameKindAndDescription() {
            Folio folio = stayFolio();
            Charge night = folio.post(roomNight("180.00"));

            Charge reversal = folio.reverse(night.id(), "Diaria em duplicidade");

            assertThat(reversal).isInstanceOf(RoomNightCharge.class);
            assertThat(reversal.amount()).isEqualTo(money("-180.00"));
            assertThat(reversal.description()).isEqualTo(night.description());
            assertThat(reversal.reversalOf()).contains(night.id());
            assertThat(reversal.reason()).contains("Diaria em duplicidade");
            assertThat(reversal.isReversal()).isTrue();
        }

        @Test
        void shouldReverseATabChargeWithATabCharge() {
            Folio folio = tabFolio();
            Charge tab = folio.post(consumption("47.30"));

            Charge reversal = folio.reverse(tab.id(), "Lancada na comanda errada");

            assertThat(reversal).isInstanceOf(TabCharge.class);
            assertThat(reversal.amount()).isEqualTo(money("-47.30"));
        }

        @Test
        void shouldKeepTheOriginalChargeIntact() {
            Folio folio = stayFolio();
            Charge night = folio.post(roomNight("180.00"));

            Charge reversal = folio.reverse(night.id(), "Diaria em duplicidade");

            assertThat(folio.charges()).containsExactly(night, reversal);
            assertThat(night.amount()).isEqualTo(money("180.00"));
            assertThat(night.isReversal()).isFalse();
        }

        @Test
        void shouldRejectReversingTheSameChargeTwice() {
            Folio folio = stayFolio();
            Charge night = folio.post(roomNight("180.00"));
            folio.reverse(night.id(), "Diaria em duplicidade");

            assertRejectedWith(() -> folio.reverse(night.id(), "De novo"), CHARGE_ALREADY_REVERSED);
        }

        @Test
        void shouldRejectReversingAReversal() {
            Folio folio = stayFolio();
            Charge night = folio.post(roomNight("180.00"));
            Charge reversal = folio.reverse(night.id(), "Diaria em duplicidade");

            assertRejectedWith(() -> folio.reverse(reversal.id(), "Desfazer o estorno"), CHARGE_NOT_REVERSIBLE);
        }

        @Test
        void shouldRejectReversingAnAdjustment() {
            Folio folio = stayFolio();
            AdjustmentCharge adjustment = adjust(folio, "-20.00");

            assertRejectedWith(() -> folio.reverse(adjustment.id(), "Ajuste errado"), CHARGE_NOT_REVERSIBLE);
        }

        @Test
        void shouldRejectReversingAChargeOfAnotherFolio() {
            Folio folio = stayFolio();
            Folio otherFolio = stayFolio();
            Charge otherNight = otherFolio.post(roomNight("180.00"));

            assertRejectedWith(() -> folio.reverse(otherNight.id(), "Engano"), CHARGE_NOT_FOUND);
        }

        @Test
        void shouldRejectReversingAChargeThatDoesNotExist() {
            Folio folio = stayFolio();

            assertRejectedWith(() -> folio.reverse(ChargeId.newId(), "Engano"), CHARGE_NOT_FOUND);
        }

        @Test
        void shouldRejectAReversalWithoutReason() {
            Folio folio = stayFolio();
            Charge night = folio.post(roomNight("180.00"));

            assertRejectedWith(() -> folio.reverse(night.id(), "  "), INVALID_CHARGE_REASON);
        }

        @Test
        void shouldAllowReversingTheOriginalAgainWhenTheFirstReversalWasRefused() {
            Folio folio = stayFolio();
            Charge night = folio.post(roomNight("180.00"));
            assertThatThrownBy(() -> folio.reverse(night.id(), " "));

            Charge reversal = folio.reverse(night.id(), "Diaria em duplicidade");

            assertThat(reversal.reversalOf()).contains(night.id());
        }
    }
}
