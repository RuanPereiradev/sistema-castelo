package br.com.castel.finance.web;

import br.com.castel.billing.api.PaymentMethod;
import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/finance/expenses/{expenseId}/payment}: how the account payable is settled. */
public class ExpensePaymentRequest {

    @NotNull
    private PaymentMethod method;

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }
}
