package br.com.castel.finance.web;

/**
 * Body of {@code POST /api/finance/expenses/{expenseId}/cancel}.
 *
 * <p>The reason is not checked here: absent or blank is a rule of the domain and answers
 * {@code INVALID_EXPENSE_CANCELLATION}, the same code a reason past 500 characters gets.
 */
public class ExpenseCancellationRequest {

    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
