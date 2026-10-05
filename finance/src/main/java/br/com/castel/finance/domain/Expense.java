package br.com.castel.finance.domain;

import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.sharedkernel.AuditedEntity;
import br.com.castel.sharedkernel.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Money that left, or is owed and will leave.
 *
 * <p><b>Two dates, because profit and cash flow are different numbers</b> (decision D1 of task F1):
 *
 * <ul>
 *   <li>{@link #accrualDate()} says which month the expense belongs to. The rent paid on the 5th is
 *       the whole month's expense, so the result of the month is right.
 *   <li>{@link #paidAt()} says when the money actually left, which is what the cash flow counts.
 *       It is <b>empty while the expense is an account payable</b>.
 * </ul>
 *
 * <p>Keeping one date only would leave one of the two questions unanswerable: either a month where
 * two payrolls were paid shows a loss that did not happen, or the cash flow is a guess.
 *
 * <p>Nothing is ever deleted, as everywhere else in this system. A mistake is cancelled with a
 * reason and stays, so that a number someone read yesterday can still be explained today. A paid
 * expense is not cancelled: reverse the payment first.
 */
@Entity
@Table(name = "expense")
@DynamicUpdate
public class Expense extends AuditedEntity {

    /** Maximum length of the description, after trimming. */
    public static final int MAXIMUM_DESCRIPTION_LENGTH = 200;

    /** Maximum length of the supplier's name, after trimming. */
    public static final int MAXIMUM_SUPPLIER_NAME_LENGTH = 200;

    /** Maximum length of a cancellation reason, after trimming. */
    public static final int MAXIMUM_CANCELLATION_REASON_LENGTH = 500;

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private ExpenseId id;

    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20, updatable = false)
    private ExpenseCategory category;

    @Column(name = "description", nullable = false, updatable = false)
    private String description;

    @Column(name = "amount", nullable = false, updatable = false)
    private Money amount;

    @Column(name = "accrual_date", nullable = false, updatable = false)
    private LocalDate accrualDate;

    @Column(name = "due_date", updatable = false)
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 20)
    private PaymentMethod method;

    @Column(name = "supplier_name", updatable = false)
    private String supplierName;

    @Column(name = "employee_id", updatable = false)
    private UUID employeeId;

    /** The drawer the cash left, when it was paid in cash (decision D4). */
    @Column(name = "cash_drawer_session_id")
    private UUID cashDrawerSessionId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    protected Expense() {
        // for JPA
    }

    private Expense(UUID propertyId, ExpenseCategory category, String description, Money amount,
            LocalDate accrualDate, LocalDate dueDate, String supplierName, UUID employeeId) {
        this.id = ExpenseId.newId();
        this.propertyId = propertyId;
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.accrualDate = accrualDate;
        this.dueDate = dueDate;
        this.supplierName = supplierName;
        this.employeeId = employeeId;
    }

    /**
     * An account payable: the expense exists, belongs to its month, and the money has not left yet.
     *
     * @param accrualDate the month the expense belongs to, which is not always today
     * @param dueDate when it has to be paid; null when there is no date
     * @throws InvalidExpenseAmountException if the amount is not above zero
     * @throws InvalidExpenseDescriptionException if the description is missing or past 200 characters
     * @throws InvalidExpenseDatesException if the due date falls before the accrual date
     * @throws InvalidExpenseEmployeeException if an employee comes on anything but payroll
     */
    public static Expense toPay(UUID propertyId, ExpenseCategory category, String description,
            Money amount, LocalDate accrualDate, LocalDate dueDate, String supplierName, UUID employeeId) {
        requireFields(propertyId, category, amount, accrualDate);
        if (dueDate != null && dueDate.isBefore(accrualDate)) {
            throw new InvalidExpenseDatesException(
                    "An expense cannot be due before the month it belongs to");
        }
        requireEmployeeOnlyOnPayroll(category, employeeId);
        return new Expense(propertyId, category, requireValidDescription(description), amount,
                accrualDate, dueDate, trimmedSupplier(supplierName), employeeId);
    }

    /**
     * An expense paid on the spot, which is what happens at the counter: it is registered and
     * settled in one movement.
     *
     * <p>Paying in cash names the drawer session the money left, because the blind closing counts
     * on it (decision D4): without the movement, the expected amount would ignore the payment and
     * the count would report a shortfall that looks like theft.
     *
     * @throws CashExpenseRequiresDrawerSessionException if cash comes without a drawer session
     */
    public static Expense paidNow(UUID propertyId, ExpenseCategory category, String description,
            Money amount, LocalDate accrualDate, String supplierName, UUID employeeId,
            PaymentMethod method, UUID cashDrawerSessionId, UUID paidBy, Instant paidAt) {
        Expense expense = toPay(propertyId, category, description, amount, accrualDate, null,
                supplierName, employeeId);
        expense.settle(method, cashDrawerSessionId, paidBy, paidAt);
        return expense;
    }

    /**
     * Pays an account payable. The amount and the month do not change: only when the money left,
     * how, and who sent it.
     *
     * @throws ExpenseCancelledException if the expense was cancelled
     * @throws ExpenseAlreadyPaidException if it was already paid; the first payment stays
     * @throws CashExpenseRequiresDrawerSessionException if cash comes without a drawer session
     */
    public void pay(PaymentMethod method, UUID cashDrawerSessionId, UUID paidBy, Instant paidAt) {
        requireNotCancelled();
        if (isPaid()) {
            throw new ExpenseAlreadyPaidException("Expense " + id.value() + " was already paid");
        }
        settle(method, cashDrawerSessionId, paidBy, paidAt);
    }

    /**
     * Cancels an expense registered by mistake. It stays on the books, cancelled, with its author
     * and reason, and stops weighing on the result and on the cash flow.
     *
     * <p>A paid expense is not cancelled: the money already left, and pretending otherwise would
     * make the drawer and the books disagree. Reverse the payment first.
     *
     * @throws ExpenseAlreadyPaidException if the expense was paid
     * @throws ExpenseCancelledException if it was already cancelled; the first author and reason stay
     * @throws InvalidExpenseCancellationException if the reason is missing, blank or past 500 characters
     */
    public void cancel(String reason, UUID cancelledBy, Instant cancelledAt) {
        Objects.requireNonNull(cancelledBy, "cancelledBy");
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        requireNotCancelled();
        if (isPaid()) {
            throw new ExpenseAlreadyPaidException(
                    "Expense " + id.value() + " was paid and cannot be cancelled; reverse the payment first");
        }
        this.cancellationReason = requireValidReason(reason);
        this.cancelledBy = cancelledBy;
        this.cancelledAt = cancelledAt;
    }

    private void settle(PaymentMethod method, UUID cashDrawerSessionId, UUID paidBy, Instant paidAt) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(paidBy, "paidBy");
        Objects.requireNonNull(paidAt, "paidAt");
        if (method == PaymentMethod.CASH && cashDrawerSessionId == null) {
            throw new CashExpenseRequiresDrawerSessionException(
                    "An expense paid in cash leaves the drawer and needs an open session");
        }
        this.method = method;
        this.cashDrawerSessionId = method == PaymentMethod.CASH ? cashDrawerSessionId : null;
        this.paidBy = paidBy;
        this.paidAt = paidAt;
    }

    private void requireNotCancelled() {
        if (isCancelled()) {
            throw new ExpenseCancelledException("Expense " + id.value() + " is cancelled");
        }
    }

    private static void requireFields(UUID propertyId, ExpenseCategory category, Money amount,
            LocalDate accrualDate) {
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(accrualDate, "accrualDate");
        if (!amount.isPositive()) {
            throw new InvalidExpenseAmountException("An expense is above zero");
        }
    }

    private static void requireEmployeeOnlyOnPayroll(ExpenseCategory category, UUID employeeId) {
        if (employeeId != null && !category.namesAnEmployee()) {
            throw new InvalidExpenseEmployeeException(
                    "Only a payroll expense names an employee, not " + category);
        }
    }

    private static String requireValidDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new InvalidExpenseDescriptionException("An expense needs a description");
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAXIMUM_DESCRIPTION_LENGTH) {
            throw new InvalidExpenseDescriptionException(
                    "An expense description takes at most " + MAXIMUM_DESCRIPTION_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String trimmedSupplier(String supplierName) {
        if (supplierName == null || supplierName.isBlank()) {
            return null;
        }
        String trimmed = supplierName.trim();
        if (trimmed.length() > MAXIMUM_SUPPLIER_NAME_LENGTH) {
            throw new InvalidExpenseDescriptionException(
                    "A supplier name takes at most " + MAXIMUM_SUPPLIER_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String requireValidReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidExpenseCancellationException("A cancellation needs a reason");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAXIMUM_CANCELLATION_REASON_LENGTH) {
            throw new InvalidExpenseCancellationException(
                    "A cancellation reason takes at most " + MAXIMUM_CANCELLATION_REASON_LENGTH + " characters");
        }
        return trimmed;
    }

    // ------------------------------------------------------------------ reading

    /** Whether the money already left. */
    public boolean isPaid() {
        return paidAt != null;
    }

    /** Whether the expense was cancelled, and so weighs on nothing. */
    public boolean isCancelled() {
        return cancelledAt != null;
    }

    /** Owed and not paid: what the accounts payable list holds. */
    public boolean isPayable() {
        return !isPaid() && !isCancelled();
    }

    /** Owed past its due date. An expense with no due date is never overdue. */
    public boolean isOverdueOn(LocalDate today) {
        Objects.requireNonNull(today, "today");
        return isPayable() && dueDate != null && dueDate.isBefore(today);
    }

    /**
     * What this expense takes off the result of the period. Zero once cancelled, and zero for a
     * withdrawal by the owners, which moves cash without being a cost of operating.
     */
    public Money weightOnTheResult() {
        return isCancelled() || !category.weighsOnTheResult() ? Money.ZERO : amount;
    }

    /** What this expense took out of the cash on the day it was paid. Zero while it is owed. */
    public Money weightOnTheCashFlow() {
        return isPaid() && !isCancelled() ? amount : Money.ZERO;
    }

    public ExpenseId id() {
        return id;
    }

    public UUID propertyId() {
        return propertyId;
    }

    public ExpenseCategory category() {
        return category;
    }

    public String description() {
        return description;
    }

    public Money amount() {
        return amount;
    }

    /** The month the expense belongs to, which decides the result of the period. */
    public LocalDate accrualDate() {
        return accrualDate;
    }

    public Optional<LocalDate> dueDate() {
        return Optional.ofNullable(dueDate);
    }

    /** When the money left; empty while the expense is owed. */
    public Optional<Instant> paidAt() {
        return Optional.ofNullable(paidAt);
    }

    public Optional<UUID> paidBy() {
        return Optional.ofNullable(paidBy);
    }

    public Optional<PaymentMethod> method() {
        return Optional.ofNullable(method);
    }

    public Optional<String> supplierName() {
        return Optional.ofNullable(supplierName);
    }

    public Optional<UUID> employeeId() {
        return Optional.ofNullable(employeeId);
    }

    public Optional<UUID> cashDrawerSessionId() {
        return Optional.ofNullable(cashDrawerSessionId);
    }

    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    public Optional<UUID> cancelledBy() {
        return Optional.ofNullable(cancelledBy);
    }

    public Optional<String> cancellationReason() {
        return Optional.ofNullable(cancellationReason);
    }
}
