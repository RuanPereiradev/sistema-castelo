package br.com.castel.finance.application;

import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.finance.domain.ExpenseCategory;
import br.com.castel.sharedkernel.Money;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What the route asks when registering money going out.
 *
 * <p>{@code method} null means the expense is only being recorded as owed; filled means it is being
 * paid on the spot, which is what happens at the counter.
 */
public record RegisterExpenseCommand(
        ExpenseCategory category,
        String description,
        Money amount,
        LocalDate accrualDate,
        LocalDate dueDate,
        String supplierName,
        UUID employeeId,
        PaymentMethod method,
        String idempotencyKey) {

    /** Whether the expense is being paid now rather than only recorded as owed. */
    public boolean isPaidNow() {
        return method != null;
    }
}
