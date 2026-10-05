package br.com.castel.finance.web;

import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.finance.domain.ExpenseCategory;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/finance/expenses}.
 *
 * <p>Only the category is checked here, because without it there is nothing to file the expense
 * under. Everything else is a rule of the domain and answers its own code: a value that is not
 * above zero gives {@code INVALID_EXPENSE_AMOUNT}, a missing description
 * {@code INVALID_EXPENSE_DESCRIPTION}.
 *
 * <p>{@code method} absent means the expense is only being recorded as owed; filled means it is
 * being paid now.
 */
public class ExpenseRequest {

    @NotNull
    private ExpenseCategory category;

    private String description;

    /** Decimal string, as money travels everywhere in this API. */
    private String amount;

    /** The month the expense belongs to, {@code YYYY-MM-DD}; absent means today. */
    private String accrualDate;

    private String dueDate;

    private String supplierName;

    private String employeeId;

    private PaymentMethod method;

    public ExpenseCategory getCategory() {
        return category;
    }

    public void setCategory(ExpenseCategory category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getAccrualDate() {
        return accrualDate;
    }

    public void setAccrualDate(String accrualDate) {
        this.accrualDate = accrualDate;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }
}
