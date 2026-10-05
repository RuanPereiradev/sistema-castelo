package br.com.castel.finance.application;

import br.com.castel.billing.api.CashDrawerFacade;
import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.finance.domain.CashExpenseRequiresDrawerSessionException;
import br.com.castel.finance.domain.Expense;
import br.com.castel.finance.domain.ExpenseId;
import br.com.castel.finance.domain.ExpenseNotFoundException;
import br.com.castel.finance.domain.ExpenseRepository;
import br.com.castel.sharedkernel.AuditorAware;
import br.com.castel.sharedkernel.CurrentProperty;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registering, paying and cancelling money going out.
 *
 * <p>Only orchestration: every rule lives in {@link Expense}. The author is the authenticated user
 * and the moment comes from the {@link Clock}.
 *
 * <p>Cash is the one case that reaches outside this module. The money leaves the drawer, so the
 * expense and the movement of the session are written in the <b>same transaction</b> (decision D4):
 * either both or neither, or the drawer and the books would disagree and the blind closing would
 * report a shortfall nobody could explain.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final CashDrawerFacade cashDrawer;
    private final CurrentProperty currentProperty;
    private final AuditorAware auditorAware;
    private final Clock clock;

    public ExpenseService(
            ExpenseRepository expenses,
            CashDrawerFacade cashDrawer,
            CurrentProperty currentProperty,
            AuditorAware auditorAware,
            Clock clock) {
        this.expenses = expenses;
        this.cashDrawer = cashDrawer;
        this.currentProperty = currentProperty;
        this.auditorAware = auditorAware;
        this.clock = clock;
    }

    /**
     * Records money going out, owed or already paid.
     *
     * @throws CashExpenseRequiresDrawerSessionException if it is paid in cash and no drawer is open
     */
    @Transactional
    public Expense register(RegisterExpenseCommand command) {
        if (!command.isPaidNow()) {
            return expenses.save(Expense.toPay(
                    currentProperty.id(), command.category(), command.description(), command.amount(),
                    command.accrualDate(), command.dueDate(), command.supplierName(), command.employeeId()));
        }
        UUID sessionId = drawerSessionFor(command.method());
        Expense expense = Expense.paidNow(
                currentProperty.id(), command.category(), command.description(), command.amount(),
                command.accrualDate(), command.supplierName(), command.employeeId(),
                command.method(), sessionId, auditorAware.currentAuditorId(), clock.instant());
        takeFromTheDrawer(expense, command.idempotencyKey());
        return expenses.save(expense);
    }

    /**
     * Pays an account payable.
     *
     * @throws ExpenseNotFoundException if the expense does not exist
     * @throws CashExpenseRequiresDrawerSessionException if it is paid in cash and no drawer is open
     */
    @Transactional
    public Expense pay(ExpenseId expenseId, PaymentMethod method, String idempotencyKey) {
        Expense expense = loadForUpdate(expenseId);
        expense.pay(method, drawerSessionFor(method), auditorAware.currentAuditorId(), clock.instant());
        takeFromTheDrawer(expense, idempotencyKey);
        return expenses.save(expense);
    }

    /** @throws ExpenseNotFoundException if the expense does not exist */
    @Transactional
    public Expense cancel(ExpenseId expenseId, String reason) {
        Expense expense = loadForUpdate(expenseId);
        expense.cancel(reason, auditorAware.currentAuditorId(), clock.instant());
        return expenses.save(expense);
    }

    /** The expenses of a period by the month they belong to: the list the result is built from. */
    @Transactional(readOnly = true)
    public List<Expense> accruedBetween(LocalDate from, LocalDate to) {
        return expenses.findByAccrualPeriod(currentProperty.id(), from, to);
    }

    /** The expenses paid within the period: what left the cash. */
    @Transactional(readOnly = true)
    public List<Expense> paidBetween(LocalDate from, LocalDate to) {
        return expenses.findByPaymentPeriod(currentProperty.id(), from, to);
    }

    /** What is owed and not paid, due up to the date; null brings everything owed. */
    @Transactional(readOnly = true)
    public List<Expense> payable(LocalDate until) {
        return expenses.findPayable(currentProperty.id(), until);
    }

    /** @throws ExpenseNotFoundException if the expense does not exist */
    @Transactional(readOnly = true)
    public Expense find(ExpenseId expenseId) {
        return expenses.findById(expenseId).orElseThrow(() -> notFound(expenseId));
    }

    /**
     * The drawer the cash leaves, for a cash payment; null for every other method, which does not
     * touch the till. Refusing here, before the aggregate, gives the operator the reason they can
     * act on: open the drawer.
     */
    private UUID drawerSessionFor(PaymentMethod method) {
        if (method != PaymentMethod.CASH) {
            return null;
        }
        return cashDrawer.currentSessionId().orElseThrow(() -> new CashExpenseRequiresDrawerSessionException(
                "No cash drawer session is open, so cash cannot leave it"));
    }

    /** Writes the movement of the drawer in this same transaction, when the expense was cash. */
    private void takeFromTheDrawer(Expense expense, String idempotencyKey) {
        expense.cashDrawerSessionId().ifPresent(sessionId ->
                cashDrawer.payExpense(sessionId, expense.amount(), expense.description(), idempotencyKey));
    }

    private Expense loadForUpdate(ExpenseId expenseId) {
        return expenses.findByIdForUpdate(expenseId).orElseThrow(() -> notFound(expenseId));
    }

    private static ExpenseNotFoundException notFound(ExpenseId expenseId) {
        return new ExpenseNotFoundException("No expense " + expenseId.value());
    }
}
