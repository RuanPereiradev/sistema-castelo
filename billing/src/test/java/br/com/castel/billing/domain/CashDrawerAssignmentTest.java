package br.com.castel.billing.domain;

import static br.com.castel.billing.domain.FolioFixtures.assertRejectedWith;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.billing.api.PaymentMethod;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Which payment goes to which cash drawer session (invariants 20 and 21 of task 2.4). */
class CashDrawerAssignmentTest {

    private static final String CASH_DRAWER_SESSION_NOT_OPEN = "CASH_DRAWER_SESSION_NOT_OPEN";

    private static final CashDrawerSessionId OPEN_SESSION = CashDrawerSessionId.newId();

    @Test
    void shouldSendOnlyCashToTheDrawer() {
        assertThat(Arrays.stream(PaymentMethod.values()).filter(PaymentMethod::goesToCashDrawer))
                .containsExactly(PaymentMethod.CASH);
    }

    @Test
    void shouldLinkCashToTheOpenSession() {
        CashDrawerAssignment assignment = CashDrawerAssignment.of(Optional.of(OPEN_SESSION), false);

        assertThat(assignment.sessionFor(PaymentMethod.CASH)).contains(OPEN_SESSION);
    }

    @Test
    void shouldLinkCashToTheOpenSessionWhenTheControlIsOn() {
        CashDrawerAssignment assignment = CashDrawerAssignment.of(Optional.of(OPEN_SESSION), true);

        assertThat(assignment.sessionFor(PaymentMethod.CASH)).contains(OPEN_SESSION);
    }

    @Test
    void shouldLeaveCashUnlinkedWithNoSessionAndTheControlOff() {
        CashDrawerAssignment assignment = CashDrawerAssignment.of(Optional.empty(), false);

        assertThat(assignment.sessionFor(PaymentMethod.CASH)).isEmpty();
    }

    @Test
    void shouldRejectCashWithNoSessionWhenTheControlIsOn() {
        CashDrawerAssignment assignment = CashDrawerAssignment.of(Optional.empty(), true);

        assertRejectedWith(() -> assignment.sessionFor(PaymentMethod.CASH), CASH_DRAWER_SESSION_NOT_OPEN);
    }

    @Test
    void shouldNeverLinkANonCashMethodEvenWithAnOpenSession() {
        CashDrawerAssignment assignment = CashDrawerAssignment.of(Optional.of(OPEN_SESSION), true);

        assertThat(assignment.sessionFor(PaymentMethod.PIX)).isEmpty();
        assertThat(assignment.sessionFor(PaymentMethod.CREDIT_CARD)).isEmpty();
    }

    @Test
    void shouldAcceptANonCashMethodWithNoSessionWhenTheControlIsOn() {
        CashDrawerAssignment assignment = CashDrawerAssignment.of(Optional.empty(), true);

        assertThat(assignment.sessionFor(PaymentMethod.DEBIT_CARD)).isEmpty();
    }
}
