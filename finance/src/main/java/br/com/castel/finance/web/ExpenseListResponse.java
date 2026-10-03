package br.com.castel.finance.web;

import br.com.castel.finance.domain.Expense;
import java.time.LocalDate;
import java.util.List;

/**
 * A list of expenses with what it comes to, so the operator does not add it up by hand.
 *
 * @param total the sum of the listed expenses, as a decimal string
 */
public record ExpenseListResponse(List<ExpenseResponse> expenses, String total, int count) {

    public static ExpenseListResponse from(List<Expense> expenses, LocalDate today) {
        return new ExpenseListResponse(
                expenses.stream().map(expense -> ExpenseResponse.from(expense, today)).toList(),
                ExpenseResponse.totalOf(expenses),
                expenses.size());
    }
}
