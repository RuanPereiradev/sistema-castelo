package br.com.castel.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.Money;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The rules of money going out.
 *
 * <p>The dense part is the pair of dates (decision D1): the result of a period counts an expense by
 * the month it belongs to and the cash flow counts it by the day the money left, and a mistake
 * there reports a loss that did not happen.
 */
class ExpenseTest {

    private static final UUID PROPERTY = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final UUID DRAWER = UUID.randomUUID();
    private static final LocalDate OCTOBER = LocalDate.of(2026, 10, 1);
    private static final Instant ON_THE_FIFTH = Instant.parse("2026-10-05T14:00:00Z");

    @Nested
    @DisplayName("the two dates")
    class TheTwoDates {

        @Test
        void shouldKeepTheMonthItBelongsToApartFromTheDayTheMoneyLeft() {
            Expense rent = Expense.paidNow(PROPERTY, ExpenseCategory.RENT, "Aluguel de outubro",
                    Money.of("3000.00"), OCTOBER, "Imobiliária", null,
                    PaymentMethod.PIX, null, ADMIN, ON_THE_FIFTH);

            assertThat(rent.accrualDate()).isEqualTo(OCTOBER);
            assertThat(rent.paidAt()).contains(ON_THE_FIFTH);
            assertThat(rent.weightOnTheResult()).isEqualTo(Money.of("3000.00"));
            assertThat(rent.weightOnTheCashFlow()).isEqualTo(Money.of("3000.00"));
        }

        @Test
        void shouldWeighOnTheResultButNotOnTheCashWhileItIsOwed() {
            Expense owed = payable(Money.of("3000.00"));

            assertThat(owed.isPayable()).isTrue();
            assertThat(owed.weightOnTheResult())
                    .as("the month already owes it, even unpaid")
                    .isEqualTo(Money.of("3000.00"));
            assertThat(owed.weightOnTheCashFlow())
                    .as("no money left yet")
                    .isEqualTo(Money.ZERO);
        }

        @Test
        void shouldRejectADueDateBeforeTheMonthItBelongsTo() {
            assertThatCode(() -> Expense.toPay(PROPERTY, ExpenseCategory.SUPPLIER, "Carne",
                    Money.of("500.00"), OCTOBER, OCTOBER.minusDays(1), null, null))
                    .isEqualTo(InvalidExpenseDatesException.CODE);
        }
    }

    @Nested
    @DisplayName("what weighs on the result")
    class WhatWeighsOnTheResult {

        /** A withdrawal moves cash without being a cost: counting it would invent a loss. */
        @Test
        void shouldKeepAnOwnersWithdrawalOutOfTheResultButInTheCash() {
            Expense withdrawal = Expense.paidNow(PROPERTY, ExpenseCategory.WITHDRAWAL, "Retirada",
                    Money.of("5000.00"), OCTOBER, null, null,
                    PaymentMethod.PIX, null, ADMIN, ON_THE_FIFTH);

            assertThat(withdrawal.weightOnTheResult()).isEqualTo(Money.ZERO);
            assertThat(withdrawal.weightOnTheCashFlow()).isEqualTo(Money.of("5000.00"));
        }

        @ParameterizedTest
        @EnumSource(value = ExpenseCategory.class, names = "WITHDRAWAL", mode = EnumSource.Mode.EXCLUDE)
        void shouldWeighOnTheResultForEveryCategoryThatIsACost(ExpenseCategory category) {
            Expense expense = Expense.toPay(PROPERTY, category, "Gasto", Money.of("100.00"),
                    OCTOBER, null, null, category.namesAnEmployee() ? null : null);

            assertThat(expense.weightOnTheResult()).isEqualTo(Money.of("100.00"));
        }

        @Test
        void shouldWeighOnNothingOnceCancelled() {
            Expense expense = payable(Money.of("400.00"));

            expense.cancel("Lançada em duplicidade", ADMIN, ON_THE_FIFTH);

            assertThat(expense.weightOnTheResult()).isEqualTo(Money.ZERO);
            assertThat(expense.weightOnTheCashFlow()).isEqualTo(Money.ZERO);
            assertThat(expense.isPayable()).isFalse();
        }
    }

    @Nested
    @DisplayName("cash leaves the drawer")
    class CashLeavesTheDrawer {

        @Test
        void shouldNameTheDrawerTheCashLeft() {
            Expense expense = Expense.paidNow(PROPERTY, ExpenseCategory.SUPPLIER, "Peixe do dia",
                    Money.of("200.00"), OCTOBER, "Peixaria", null,
                    PaymentMethod.CASH, DRAWER, ADMIN, ON_THE_FIFTH);

            assertThat(expense.cashDrawerSessionId()).contains(DRAWER);
        }

        @Test
        void shouldRejectCashWithoutADrawerSession() {
            assertThatCode(() -> Expense.paidNow(PROPERTY, ExpenseCategory.SUPPLIER, "Peixe",
                    Money.of("200.00"), OCTOBER, null, null,
                    PaymentMethod.CASH, null, ADMIN, ON_THE_FIFTH))
                    .isEqualTo(CashExpenseRequiresDrawerSessionException.CODE);
        }

        /** Only cash touches the till, so no other method may claim a drawer. */
        @Test
        void shouldNotKeepADrawerForAMethodThatDoesNotTouchTheTill() {
            Expense expense = Expense.paidNow(PROPERTY, ExpenseCategory.SUPPLIER, "Peixe",
                    Money.of("200.00"), OCTOBER, null, null,
                    PaymentMethod.PIX, DRAWER, ADMIN, ON_THE_FIFTH);

            assertThat(expense.cashDrawerSessionId()).isEmpty();
        }
    }

    @Nested
    @DisplayName("paying an account payable")
    class PayingAnAccountPayable {

        @Test
        void shouldSettleWithoutChangingTheAmountOrTheMonth() {
            Expense owed = payable(Money.of("3000.00"));

            owed.pay(PaymentMethod.PIX, null, ADMIN, ON_THE_FIFTH);

            assertThat(owed.isPaid()).isTrue();
            assertThat(owed.amount()).isEqualTo(Money.of("3000.00"));
            assertThat(owed.accrualDate()).isEqualTo(OCTOBER);
            assertThat(owed.method()).contains(PaymentMethod.PIX);
            assertThat(owed.paidBy()).contains(ADMIN);
        }

        @Test
        void shouldRefuseASecondPaymentKeepingTheFirst() {
            Expense owed = payable(Money.of("3000.00"));
            owed.pay(PaymentMethod.PIX, null, ADMIN, ON_THE_FIFTH);

            assertThatCode(() -> owed.pay(PaymentMethod.DEBIT_CARD, null, EMPLOYEE,
                    ON_THE_FIFTH.plusSeconds(60)))
                    .isEqualTo(ExpenseAlreadyPaidException.CODE);
            assertThat(owed.method()).contains(PaymentMethod.PIX);
            assertThat(owed.paidAt()).contains(ON_THE_FIFTH);
        }

        @Test
        void shouldRefusePayingACancelledExpense() {
            Expense owed = payable(Money.of("3000.00"));
            owed.cancel("Não era nossa", ADMIN, ON_THE_FIFTH);

            assertThatCode(() -> owed.pay(PaymentMethod.PIX, null, ADMIN, ON_THE_FIFTH))
                    .isEqualTo(ExpenseCancelledException.CODE);
        }
    }

    @Nested
    @DisplayName("cancelling")
    class Cancelling {

        /** The money already left; pretending otherwise makes the drawer and the books disagree. */
        @Test
        void shouldRefuseCancellingWhatWasAlreadyPaid() {
            Expense paid = Expense.paidNow(PROPERTY, ExpenseCategory.RENT, "Aluguel",
                    Money.of("3000.00"), OCTOBER, null, null,
                    PaymentMethod.PIX, null, ADMIN, ON_THE_FIFTH);

            assertThatCode(() -> paid.cancel("Errei", ADMIN, ON_THE_FIFTH))
                    .isEqualTo(ExpenseAlreadyPaidException.CODE);
            assertThat(paid.isCancelled()).isFalse();
        }

        @Test
        void shouldKeepTheFirstAuthorAndReasonOnASecondCancellation() {
            Expense expense = payable(Money.of("100.00"));
            expense.cancel("Primeiro motivo", ADMIN, ON_THE_FIFTH);

            assertThatCode(() -> expense.cancel("Segundo motivo", EMPLOYEE, ON_THE_FIFTH))
                    .isEqualTo(ExpenseCancelledException.CODE);
            assertThat(expense.cancellationReason()).contains("Primeiro motivo");
            assertThat(expense.cancelledBy()).contains(ADMIN);
        }

        @Test
        void shouldRejectACancellationWithoutAReason() {
            Expense expense = payable(Money.of("100.00"));

            assertThatCode(() -> expense.cancel("   ", ADMIN, ON_THE_FIFTH))
                    .isEqualTo(InvalidExpenseCancellationException.CODE);
        }
    }

    @Nested
    @DisplayName("what the aggregate refuses to be built from")
    class WhatItRefuses {

        @Test
        void shouldRejectAnAmountThatIsNotAboveZero() {
            assertThatCode(() -> Expense.toPay(PROPERTY, ExpenseCategory.OTHER, "Nada",
                    Money.ZERO, OCTOBER, null, null, null))
                    .isEqualTo(InvalidExpenseAmountException.CODE);
        }

        @Test
        void shouldRejectAMissingDescription() {
            assertThatCode(() -> Expense.toPay(PROPERTY, ExpenseCategory.OTHER, "  ",
                    Money.of("10.00"), OCTOBER, null, null, null))
                    .isEqualTo(InvalidExpenseDescriptionException.CODE);
        }

        @Test
        void shouldRejectADescriptionPastTheLimit() {
            String tooLong = "a".repeat(Expense.MAXIMUM_DESCRIPTION_LENGTH + 1);

            assertThatCode(() -> Expense.toPay(PROPERTY, ExpenseCategory.OTHER, tooLong,
                    Money.of("10.00"), OCTOBER, null, null, null))
                    .isEqualTo(InvalidExpenseDescriptionException.CODE);
        }

        /** Only payroll names an employee; the database refuses it on anything else too. */
        @Test
        void shouldRejectAnEmployeeOnSomethingThatIsNotPayroll() {
            assertThatCode(() -> Expense.toPay(PROPERTY, ExpenseCategory.RENT, "Aluguel",
                    Money.of("3000.00"), OCTOBER, null, null, EMPLOYEE))
                    .isEqualTo(InvalidExpenseEmployeeException.CODE);
        }

        @Test
        void shouldAcceptAnEmployeeOnPayroll() {
            Expense payroll = Expense.toPay(PROPERTY, ExpenseCategory.PAYROLL, "Salário de outubro",
                    Money.of("2200.00"), OCTOBER, OCTOBER.plusDays(4), null, EMPLOYEE);

            assertThat(payroll.employeeId()).contains(EMPLOYEE);
            assertThat(payroll.category().namesAnEmployee()).isTrue();
        }
    }

    @Nested
    @DisplayName("overdue")
    class Overdue {

        @Test
        void shouldBeOverdueOnlyAfterTheDueDateAndWhileUnpaid() {
            Expense owed = Expense.toPay(PROPERTY, ExpenseCategory.SUPPLIER, "Carne",
                    Money.of("500.00"), OCTOBER, OCTOBER.plusDays(10), "Frigorífico", null);

            assertThat(owed.isOverdueOn(OCTOBER.plusDays(10))).as("on the day, not yet").isFalse();
            assertThat(owed.isOverdueOn(OCTOBER.plusDays(11))).isTrue();

            owed.pay(PaymentMethod.PIX, null, ADMIN, ON_THE_FIFTH);
            assertThat(owed.isOverdueOn(OCTOBER.plusDays(11)))
                    .as("paid is never overdue")
                    .isFalse();
        }

        @Test
        void shouldNeverBeOverdueWithoutADueDate() {
            Expense owed = payable(Money.of("100.00"));

            assertThat(owed.isOverdueOn(OCTOBER.plusYears(1))).isFalse();
        }
    }

    // ------------------------------------------------------------- helpers

    private static Expense payable(Money amount) {
        return Expense.toPay(PROPERTY, ExpenseCategory.RENT, "Aluguel de outubro", amount,
                OCTOBER, null, "Imobiliária", null);
    }

    /** The stable code of the domain exception thrown, which is the contract the front reads. */
    private static org.assertj.core.api.AbstractStringAssert<?> assertThatCode(
            ThrowingCallable action) {
        Throwable thrown = catchThrowable(action);
        assertThat(thrown).isInstanceOf(DomainException.class);
        return assertThat(((DomainException) thrown).code());
    }
}
