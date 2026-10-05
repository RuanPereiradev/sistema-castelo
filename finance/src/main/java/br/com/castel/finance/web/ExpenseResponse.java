package br.com.castel.finance.web;

import br.com.castel.finance.domain.Expense;
import br.com.castel.sharedkernel.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An expense as the {@code ADMIN} reads it. Money as a decimal string, dates as {@code YYYY-MM-DD},
 * instants ISO-8601; what does not apply is null.
 *
 * <p>The two dates are both here on purpose: {@code accrualDate} is the month the expense belongs
 * to, which decides the result of the period, and {@code paidAt} is when the money left, which is
 * what the cash flow counts.
 */
public record ExpenseResponse(
        String id,
        String category,
        String description,
        String amount,
        String accrualDate,
        String dueDate,
        String paidAt,
        String paidBy,
        String method,
        String supplierName,
        String employeeId,
        String cashDrawerSessionId,
        boolean paid,
        boolean payable,
        boolean overdue,
        String cancelledAt,
        String cancelledBy,
        String cancellationReason) {

    /** @param today the day the overdue flag is read against */
    public static ExpenseResponse from(Expense expense, LocalDate today) {
        return new ExpenseResponse(
                expense.id().value().toString(),
                expense.category().name(),
                expense.description(),
                expense.amount().asString(),
                expense.accrualDate().toString(),
                expense.dueDate().map(LocalDate::toString).orElse(null),
                expense.paidAt().map(Instant::toString).orElse(null),
                expense.paidBy().map(UUID::toString).orElse(null),
                expense.method().map(Enum::name).orElse(null),
                expense.supplierName().orElse(null),
                expense.employeeId().map(UUID::toString).orElse(null),
                expense.cashDrawerSessionId().map(UUID::toString).orElse(null),
                expense.isPaid(),
                expense.isPayable(),
                expense.isOverdueOn(today),
                expense.cancelledAt().map(Instant::toString).orElse(null),
                expense.cancelledBy().map(UUID::toString).orElse(null),
                expense.cancellationReason().orElse(null));
    }

    /** What the money came to, for the list header. */
    public static String totalOf(java.util.List<Expense> expenses) {
        return expenses.stream()
                .map(Expense::amount)
                .reduce(Money.ZERO, Money::plus)
                .asString();
    }
}
