package br.com.castel.finance.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link Expense}. Implemented in {@code finance.infra}. */
public interface ExpenseRepository {

    Optional<Expense> findById(ExpenseId id);

    /**
     * The expense with its row locked {@code FOR UPDATE}, for paying or cancelling it. Two
     * operators settling the same account payable queue on that row, so the second one finds it
     * already paid instead of overwriting the first payment.
     */
    Optional<Expense> findByIdForUpdate(ExpenseId id);

    Expense save(Expense expense);

    /**
     * The expenses of a period <b>by accrual date</b> — which month they belong to, not when they
     * were paid. This is the list the result of the period is built from. Cancelled ones are left
     * out. Newest first.
     */
    List<Expense> findByAccrualPeriod(UUID propertyId, LocalDate from, LocalDate to);

    /**
     * The expenses <b>paid</b> within the period, which is what the cash flow counts. An expense
     * accrued in October and paid in November appears here in November.
     */
    List<Expense> findByPaymentPeriod(UUID propertyId, LocalDate from, LocalDate to);

    /**
     * What is owed and not paid, oldest due date first, with the ones without a due date last.
     *
     * @param until only what is due up to this date; null brings everything owed
     */
    List<Expense> findPayable(UUID propertyId, LocalDate until);
}
