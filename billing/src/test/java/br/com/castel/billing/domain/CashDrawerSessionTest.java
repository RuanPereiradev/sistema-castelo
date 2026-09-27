package br.com.castel.billing.domain;

import static br.com.castel.billing.domain.FolioFixtures.ADMIN;
import static br.com.castel.billing.domain.FolioFixtures.LATER;
import static br.com.castel.billing.domain.FolioFixtures.OPENED_AT;
import static br.com.castel.billing.domain.FolioFixtures.OPERATOR;
import static br.com.castel.billing.domain.FolioFixtures.PROPERTY_ID;
import static br.com.castel.billing.domain.FolioFixtures.assertRejectedWith;
import static br.com.castel.billing.domain.FolioFixtures.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.sharedkernel.Money;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Cash drawer session, written from the spec of task 2.4 (sections 3 and 7). */
class CashDrawerSessionTest {

    private static final String INVALID_OPENING_FLOAT = "INVALID_OPENING_FLOAT";
    private static final String INVALID_CASH_MOVEMENT_AMOUNT = "INVALID_CASH_MOVEMENT_AMOUNT";
    private static final String INVALID_CASH_MOVEMENT_REASON = "INVALID_CASH_MOVEMENT_REASON";
    private static final String INVALID_COUNTED_AMOUNT = "INVALID_COUNTED_AMOUNT";
    private static final String INVALID_CASH_CLOSING_NOTE = "INVALID_CASH_CLOSING_NOTE";
    private static final String CASH_DRAWER_SESSION_CLOSED = "CASH_DRAWER_SESSION_CLOSED";
    private static final String CASH_DRAWER_SESSION_NOT_OWNED = "CASH_DRAWER_SESSION_NOT_OWNED";
    private static final String INVALID_IDEMPOTENCY_KEY = "INVALID_IDEMPOTENCY_KEY";
    private static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";

    private static final String KEY = "drop-7f3a";
    private static final UUID OTHER_FRONT_DESK = UUID.randomUUID();

    private static CashDrawerSession openWith(String openingFloat) {
        return CashDrawerSession.open(PROPERTY_ID, money(openingFloat), OPERATOR, OPENED_AT);
    }

    private static CashMovement drop(CashDrawerSession session, String amount) {
        return session.drop(money(amount), "Sangria para o cofre", UUID.randomUUID().toString());
    }

    private static CashMovement supply(CashDrawerSession session, String amount) {
        return session.supply(money(amount), "Troco do cofre", UUID.randomUUID().toString());
    }

    /** Closes by the operator who opened, counting {@code counted} against {@code cashPayments}. */
    private static void close(CashDrawerSession session, String counted, String cashPayments, String note) {
        session.close(money(counted), money(cashPayments), note, OPERATOR, false, LATER);
    }

    private static CashDrawerSession closedSession() {
        CashDrawerSession session = openWith("100.00");
        close(session, "100.00", "0.00", null);
        return session;
    }

    @Nested
    @DisplayName("open (invariants 1 and 2)")
    class Open {

        @Test
        void shouldBeBornOpenWithTheFloatAndWhoOpened() {
            CashDrawerSession session = openWith("100.00");

            assertThat(session.status()).isEqualTo(CashDrawerSessionStatus.OPEN);
            assertThat(session.isOpen()).isTrue();
            assertThat(session.propertyId()).isEqualTo(PROPERTY_ID);
            assertThat(session.openingFloat()).isEqualTo(money("100.00"));
            assertThat(session.openedBy()).isEqualTo(OPERATOR);
            assertThat(session.openedAt()).isEqualTo(OPENED_AT);
            assertThat(session.movements()).isEmpty();
        }

        @Test
        void shouldAcceptAZeroOpeningFloat() {
            CashDrawerSession session = openWith("0.00");

            assertThat(session.openingFloat()).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldRejectANegativeOpeningFloat() {
            assertRejectedWith(() -> openWith("-0.01"), INVALID_OPENING_FLOAT);
        }

        @Test
        void shouldRejectAMissingOpeningFloat() {
            assertRejectedWith(
                    () -> CashDrawerSession.open(PROPERTY_ID, null, OPERATOR, OPENED_AT), INVALID_OPENING_FLOAT);
        }

        @Test
        void shouldExposeNothingOfTheClosingWhileOpen() {
            CashDrawerSession session = openWith("100.00");

            assertThat(session.frozenExpectedAmount()).isEmpty();
            assertThat(session.frozenCashPayments()).isEmpty();
            assertThat(session.difference()).isEmpty();
            assertThat(session.countedAmount()).isEmpty();
            assertThat(session.closedBy()).isEmpty();
            assertThat(session.closedAt()).isEmpty();
        }
    }

    @Nested
    @DisplayName("expected amount (invariant 9)")
    class ExpectedAmount {

        @Test
        void shouldBeTheOpeningFloatAloneWithNoMovementOrPayment() {
            CashDrawerSession session = openWith("100.00");

            assertThat(session.expectedAmount(Money.ZERO)).isEqualTo(money("100.00"));
        }

        @Test
        void shouldBeZeroWithAZeroFloatAndNothingElse() {
            CashDrawerSession session = openWith("0.00");

            assertThat(session.expectedAmount(Money.ZERO)).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldAddTheCashPayments() {
            CashDrawerSession session = openWith("100.00");

            assertThat(session.expectedAmount(money("150.00"))).isEqualTo(money("250.00"));
        }

        @Test
        void shouldAddSuppliesAndSubtractDrops() {
            CashDrawerSession session = openWith("100.00");
            supply(session, "50.00");
            drop(session, "30.00");
            drop(session, "20.00");

            assertThat(session.totalSupplies()).isEqualTo(money("50.00"));
            assertThat(session.totalDrops()).isEqualTo(money("50.00"));
            assertThat(session.expectedAmount(money("150.00"))).isEqualTo(money("250.00"));
        }

        @Test
        void shouldKeepEveryCent() {
            CashDrawerSession session = openWith("0.01");
            supply(session, "0.01");
            drop(session, "0.01");

            assertThat(session.expectedAmount(money("0.01"))).isEqualTo(money("0.02"));
        }

        @Test
        void shouldAcceptADropAboveTheExpectedAndGoNegative() {
            CashDrawerSession session = openWith("100.00");

            drop(session, "100.01");

            assertThat(session.expectedAmount(Money.ZERO)).isEqualTo(money("-0.01"));
        }

        @Test
        void shouldIgnoreTheArgumentOnceClosed() {
            CashDrawerSession session = openWith("100.00");
            close(session, "150.00", "50.00", null);

            assertThat(session.expectedAmount(money("999.00"))).isEqualTo(money("150.00"));
        }
    }

    @Nested
    @DisplayName("close and difference (invariants 10 to 13, 17 and 18)")
    class Close {

        @Test
        void shouldFreezeTheExpectedAmountAndRecordTheClosing() {
            CashDrawerSession session = openWith("100.00");
            supply(session, "50.00");
            drop(session, "200.00");

            session.close(money("99.50"), money("150.00"), "Troco errado", OPERATOR, false, LATER);

            assertThat(session.status()).isEqualTo(CashDrawerSessionStatus.CLOSED);
            assertThat(session.isOpen()).isFalse();
            assertThat(session.frozenExpectedAmount()).contains(money("100.00"));
            assertThat(session.countedAmount()).contains(money("99.50"));
            assertThat(session.closingNote()).contains("Troco errado");
            assertThat(session.closedBy()).contains(OPERATOR);
            assertThat(session.closedAt()).contains(LATER);
        }

        @Test
        void shouldDeriveTheFrozenCashPaymentsFromTheFrozenExpected() {
            CashDrawerSession session = openWith("100.00");
            supply(session, "50.00");
            drop(session, "200.00");

            close(session, "100.00", "150.00", null);

            assertThat(session.frozenCashPayments()).contains(money("150.00"));
        }

        @Test
        void shouldReportASurplusOfOneCentAsAPositiveDifference() {
            CashDrawerSession session = openWith("100.00");

            close(session, "100.01", "0.00", "Sobra de um centavo");

            assertThat(session.difference()).contains(money("0.01"));
        }

        @Test
        void shouldReportAShortfallOfOneCentAsANegativeDifference() {
            CashDrawerSession session = openWith("100.00");

            close(session, "99.99", "0.00", "Falta de um centavo");

            assertThat(session.difference()).contains(money("-0.01"));
        }

        @Test
        void shouldReportZeroDifferenceWhenTheCountMatches() {
            CashDrawerSession session = openWith("100.00");

            close(session, "100.00", "0.00", null);

            assertThat(session.difference()).contains(Money.ZERO);
        }

        @Test
        void shouldCloseWithAShortfallInsteadOfBlocking() {
            CashDrawerSession session = openWith("100.00");

            close(session, "0.00", "500.00", "Assalto, BO 123");

            assertThat(session.isOpen()).isFalse();
            assertThat(session.difference()).contains(money("-600.00"));
        }

        @Test
        void shouldAcceptACountOfZero() {
            CashDrawerSession session = openWith("0.00");

            close(session, "0.00", "0.00", null);

            assertThat(session.countedAmount()).contains(Money.ZERO);
        }

        @Test
        void shouldRejectANegativeCount() {
            CashDrawerSession session = openWith("0.00");

            assertRejectedWith(() -> close(session, "-0.01", "0.00", "nota"), INVALID_COUNTED_AMOUNT);
        }

        @Test
        void shouldRejectAMissingCount() {
            CashDrawerSession session = openWith("0.00");

            assertRejectedWith(
                    () -> session.close(null, Money.ZERO, "nota", OPERATOR, false, LATER), INVALID_COUNTED_AMOUNT);
        }

        /** Decision #22: a note required only on a difference would reveal the expected amount of a blind closing. */
        @Test
        void shouldCloseWithoutANoteWhenTheDifferenceIsOneCent() {
            CashDrawerSession session = openWith("100.00");

            close(session, "99.99", "0.00", null);

            assertThat(session.isOpen()).isFalse();
            assertThat(session.difference()).contains(money("-0.01"));
            assertThat(session.closingNote()).isEmpty();
        }

        @Test
        void shouldTreatABlankNoteAsAbsentWhenThereIsADifference() {
            CashDrawerSession session = openWith("100.00");

            close(session, "100.01", "0.00", "   ");

            assertThat(session.closingNote()).isEmpty();
            assertThat(session.difference()).contains(money("0.01"));
        }

        @Test
        void shouldStayOpenWhenTheClosingIsRejected() {
            CashDrawerSession session = openWith("100.00");

            assertRejectedWith(() -> close(session, "99.99", "0.00", "x".repeat(501)), INVALID_CASH_CLOSING_NOTE);

            assertThat(session.isOpen()).isTrue();
            assertThat(session.frozenExpectedAmount()).isEmpty();
        }

        @Test
        void shouldAcceptANoteOfExactlyFiveHundredCharacters() {
            CashDrawerSession session = openWith("100.00");
            String note = "x".repeat(500);

            close(session, "99.00", "0.00", note);

            assertThat(session.closingNote()).contains(note);
        }

        @Test
        void shouldRejectANoteAboveFiveHundredCharactersEvenWithZeroDifference() {
            CashDrawerSession session = openWith("100.00");

            assertRejectedWith(() -> close(session, "100.00", "0.00", "x".repeat(501)), INVALID_CASH_CLOSING_NOTE);
        }

        @Test
        void shouldTreatABlankNoteAsAbsentWhenThereIsNoDifference() {
            CashDrawerSession session = openWith("100.00");

            close(session, "100.00", "0.00", "  ");

            assertThat(session.closingNote()).isEmpty();
        }

        @Test
        void shouldKeepTheFrozenValuesWhenLaterPaymentsAreOffered() {
            CashDrawerSession session = openWith("100.00");
            close(session, "140.00", "50.00", "Falta de 10");

            Money expectedAfter = session.expectedAmount(money("20.00"));

            assertThat(expectedAfter).isEqualTo(money("150.00"));
            assertThat(session.difference()).contains(money("-10.00"));
            assertThat(session.frozenCashPayments()).contains(money("50.00"));
        }
    }

    @Nested
    @DisplayName("closed session refuses writes (invariant 14)")
    class ClosedSession {

        @Test
        void shouldRejectADropWhenTheSessionIsClosed() {
            CashDrawerSession session = closedSession();

            assertRejectedWith(() -> drop(session, "10.00"), CASH_DRAWER_SESSION_CLOSED);
        }

        @Test
        void shouldRejectASupplyWhenTheSessionIsClosed() {
            CashDrawerSession session = closedSession();

            assertRejectedWith(() -> supply(session, "10.00"), CASH_DRAWER_SESSION_CLOSED);
        }

        @Test
        void shouldRejectClosingTwice() {
            CashDrawerSession session = closedSession();

            assertRejectedWith(() -> close(session, "100.00", "0.00", null), CASH_DRAWER_SESSION_CLOSED);
        }

        @Test
        void shouldKeepTheFirstClosingWhenClosingAgainIsRejected() {
            CashDrawerSession session = closedSession();

            assertRejectedWith(() -> close(session, "90.00", "0.00", "outra"), CASH_DRAWER_SESSION_CLOSED);

            assertThat(session.countedAmount()).contains(money("100.00"));
            assertThat(session.difference()).contains(Money.ZERO);
        }

        @Test
        void shouldReportClosedBeforeOwnershipWhenAnotherOperatorClosesAgain() {
            CashDrawerSession session = closedSession();

            assertRejectedWith(
                    () -> session.close(money("100.00"), Money.ZERO, null, OTHER_FRONT_DESK, false, LATER),
                    CASH_DRAWER_SESSION_CLOSED);
        }

        @Test
        void shouldOnlyAcceptMovementsWhileOpen() {
            assertThat(CashDrawerSessionStatus.OPEN.acceptsMovements()).isTrue();
            assertThat(CashDrawerSessionStatus.CLOSED.acceptsMovements()).isFalse();
        }
    }

    @Nested
    @DisplayName("who closes (invariant 15)")
    class Owner {

        @Test
        void shouldLetTheOperatorWhoOpenedClose() {
            CashDrawerSession session = openWith("100.00");

            session.close(money("100.00"), Money.ZERO, null, OPERATOR, false, LATER);

            assertThat(session.closedBy()).contains(OPERATOR);
        }

        @Test
        void shouldRejectAnotherFrontDeskOperatorClosing() {
            CashDrawerSession session = openWith("100.00");

            assertRejectedWith(
                    () -> session.close(money("100.00"), Money.ZERO, null, OTHER_FRONT_DESK, false, LATER),
                    CASH_DRAWER_SESSION_NOT_OWNED);
            assertThat(session.isOpen()).isTrue();
        }

        @Test
        void shouldLetAnAdminCloseAnotherOperatorsSession() {
            CashDrawerSession session = openWith("100.00");

            session.close(money("100.00"), Money.ZERO, null, ADMIN, true, LATER);

            assertThat(session.closedBy()).contains(ADMIN);
        }

        @Test
        void shouldCheckOwnershipBeforeTheCount() {
            CashDrawerSession session = openWith("100.00");

            assertRejectedWith(
                    () -> session.close(money("-1.00"), Money.ZERO, null, OTHER_FRONT_DESK, false, LATER),
                    CASH_DRAWER_SESSION_NOT_OWNED);
        }

        @Test
        void shouldCheckTheCountBeforeTheNote() {
            CashDrawerSession session = openWith("100.00");

            assertRejectedWith(() -> close(session, "-1.00", "0.00", "x".repeat(501)), INVALID_COUNTED_AMOUNT);
        }
    }

    @Nested
    @DisplayName("movements (invariants 4 to 8)")
    class Movements {

        @Test
        void shouldRecordADropAsANegativeSignedAmount() {
            CashDrawerSession session = openWith("100.00");

            CashMovement movement = session.drop(money("40.00"), "Cofre", KEY);

            assertThat(movement.type()).isEqualTo(CashMovementType.CASH_DROP);
            assertThat(movement.amount()).isEqualTo(money("40.00"));
            assertThat(movement.signedAmount()).isEqualTo(money("-40.00"));
            assertThat(movement.reason()).isEqualTo("Cofre");
            assertThat(movement.idempotencyKey()).isEqualTo(KEY);
            assertThat(session.movements()).containsExactly(movement);
        }

        @Test
        void shouldRecordASupplyAsAPositiveSignedAmount() {
            CashDrawerSession session = openWith("100.00");

            CashMovement movement = session.supply(money("40.00"), "Troco", KEY);

            assertThat(movement.type()).isEqualTo(CashMovementType.CASH_SUPPLY);
            assertThat(movement.signedAmount()).isEqualTo(money("40.00"));
        }

        @Test
        void shouldRejectAZeroMovement() {
            CashDrawerSession session = openWith("100.00");

            assertRejectedWith(() -> drop(session, "0.00"), INVALID_CASH_MOVEMENT_AMOUNT);
        }

        @Test
        void shouldRejectANegativeMovement() {
            CashDrawerSession session = openWith("100.00");

            assertRejectedWith(() -> supply(session, "-0.01"), INVALID_CASH_MOVEMENT_AMOUNT);
        }

        @Test
        void shouldRejectAMissingMovementAmount() {
            CashDrawerSession session = openWith("100.00");

            assertRejectedWith(() -> session.drop(null, "Cofre", KEY), INVALID_CASH_MOVEMENT_AMOUNT);
        }

        @Test
        void shouldRejectABlankReason() {
            CashDrawerSession session = openWith("100.00");

            assertRejectedWith(() -> session.drop(money("10.00"), "   ", KEY), INVALID_CASH_MOVEMENT_REASON);
        }

        @Test
        void shouldRejectAReasonAboveFiveHundredCharacters() {
            CashDrawerSession session = openWith("100.00");

            assertRejectedWith(
                    () -> session.drop(money("10.00"), "x".repeat(501), KEY), INVALID_CASH_MOVEMENT_REASON);
        }

        @Test
        void shouldAcceptAReasonOfExactlyFiveHundredCharacters() {
            CashDrawerSession session = openWith("100.00");

            CashMovement movement = session.drop(money("10.00"), "x".repeat(500), KEY);

            assertThat(movement.reason()).hasSize(500);
        }

        @Test
        void shouldNotLetTheMovementListBeChanged() {
            CashDrawerSession session = openWith("100.00");
            CashMovement movement = drop(session, "10.00");

            assertThatThrownBy(() -> session.movements().remove(movement))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldSignOnlyTheDropNegative() {
            assertThat(CashMovementType.CASH_DROP.signedAmount(money("5.00"))).isEqualTo(money("-5.00"));
            assertThat(CashMovementType.CASH_SUPPLY.signedAmount(money("5.00"))).isEqualTo(money("5.00"));
        }
    }

    @Nested
    @DisplayName("movement idempotency (invariant 4)")
    class MovementIdempotency {

        @Test
        void shouldReturnTheOriginalOnAnIdenticalRetry() {
            CashDrawerSession session = openWith("100.00");
            CashMovement original = session.drop(money("40.00"), "Cofre", KEY);

            CashMovement replay = session.drop(money("40.00"), "Cofre", KEY);

            assertThat(replay).isSameAs(original);
            assertThat(session.movements()).hasSize(1);
            assertThat(session.totalDrops()).isEqualTo(money("40.00"));
        }

        @Test
        void shouldIgnoreTheReasonWhenComparingARetry() {
            CashDrawerSession session = openWith("100.00");
            CashMovement original = session.drop(money("40.00"), "Cofre", KEY);

            CashMovement replay = session.drop(money("40.00"), "Outro texto", KEY);

            assertThat(replay).isSameAs(original);
            assertThat(replay.reason()).isEqualTo("Cofre");
        }

        @Test
        void shouldTrimTheKeyBeforeMatchingARetry() {
            CashDrawerSession session = openWith("100.00");
            CashMovement original = session.drop(money("40.00"), "Cofre", KEY);

            CashMovement replay = session.drop(money("40.00"), "Cofre", "  " + KEY + " ");

            assertThat(replay).isSameAs(original);
            assertThat(session.movementWithKey(" " + KEY)).contains(original);
        }

        @Test
        void shouldRejectTheKeyReusedWithAnotherAmount() {
            CashDrawerSession session = openWith("100.00");
            session.drop(money("40.00"), "Cofre", KEY);

            assertRejectedWith(() -> session.drop(money("40.01"), "Cofre", KEY), IDEMPOTENCY_KEY_REUSED);
        }

        @Test
        void shouldRejectTheKeyReusedWithAnotherType() {
            CashDrawerSession session = openWith("100.00");
            session.drop(money("40.00"), "Cofre", KEY);

            assertRejectedWith(() -> session.supply(money("40.00"), "Cofre", KEY), IDEMPOTENCY_KEY_REUSED);
            assertThat(session.movements()).hasSize(1);
        }

        @Test
        void shouldReplayEvenAfterTheSessionIsClosed() {
            CashDrawerSession session = openWith("100.00");
            CashMovement original = session.drop(money("40.00"), "Cofre", KEY);
            close(session, "60.00", "0.00", null);

            CashMovement replay = session.drop(money("40.00"), "Cofre", KEY);

            assertThat(replay).isSameAs(original);
        }

        @Test
        void shouldReportTheReusedKeyBeforeTheClosedSession() {
            CashDrawerSession session = openWith("100.00");
            session.drop(money("40.00"), "Cofre", KEY);
            close(session, "60.00", "0.00", null);

            assertRejectedWith(() -> session.drop(money("41.00"), "Cofre", KEY), IDEMPOTENCY_KEY_REUSED);
        }

        @Test
        void shouldReportTheReusedKeyBeforeTheInvalidAmount() {
            CashDrawerSession session = openWith("100.00");
            session.drop(money("40.00"), "Cofre", KEY);

            assertRejectedWith(() -> session.drop(money("0.00"), "Cofre", KEY), IDEMPOTENCY_KEY_REUSED);
        }

        @Test
        void shouldReportTheInvalidKeyBeforeTheClosedSession() {
            CashDrawerSession session = closedSession();

            assertRejectedWith(() -> session.drop(money("10.00"), "Cofre", " "), INVALID_IDEMPOTENCY_KEY);
        }

        @Test
        void shouldRejectAMissingKey() {
            CashDrawerSession session = openWith("100.00");

            assertRejectedWith(() -> session.drop(money("10.00"), "Cofre", null), INVALID_IDEMPOTENCY_KEY);
        }

        @Test
        void shouldRejectAKeyAboveOneHundredCharacters() {
            CashDrawerSession session = openWith("100.00");

            assertRejectedWith(
                    () -> session.drop(money("10.00"), "Cofre", "k".repeat(101)), INVALID_IDEMPOTENCY_KEY);
        }

        @Test
        void shouldAcceptAKeyOfExactlyOneHundredCharacters() {
            CashDrawerSession session = openWith("100.00");

            CashMovement movement = session.drop(money("10.00"), "Cofre", "k".repeat(100));

            assertThat(movement.idempotencyKey()).hasSize(100);
        }
    }

    @Nested
    @DisplayName("blind closing (invariant 19)")
    class BlindClosing {

        @Test
        void shouldHideTheExpectedAmountFromANonAdminWhileOpen() {
            CashDrawerSession session = openWith("100.00");

            assertThat(session.revealsExpectedAmountTo(false)).isFalse();
        }

        @Test
        void shouldRevealTheExpectedAmountToAnAdminWhileOpen() {
            CashDrawerSession session = openWith("100.00");

            assertThat(session.revealsExpectedAmountTo(true)).isTrue();
        }

        @Test
        void shouldRevealTheExpectedAmountToAnyoneOnceClosed() {
            CashDrawerSession session = closedSession();

            assertThat(session.revealsExpectedAmountTo(false)).isTrue();
            assertThat(session.revealsExpectedAmountTo(true)).isTrue();
        }
    }
}
