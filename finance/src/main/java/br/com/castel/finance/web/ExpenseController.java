package br.com.castel.finance.web;

import br.com.castel.finance.application.ExpenseService;
import br.com.castel.finance.application.RegisterExpenseCommand;
import br.com.castel.finance.domain.Expense;
import br.com.castel.finance.domain.ExpenseId;
import br.com.castel.sharedkernel.Money;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Money going out (task F1). <b>{@code ADMIN} only</b>: what the business spends is not something
 * the floor or the front desk reads, and nobody but the owner registers the rent.
 *
 * <p>Two reads of the same expenses on purpose, because they answer different questions: the
 * accrual period is what the result of the month is built from, and the payment period is what left
 * the cash.
 */
@RestController
@RequestMapping("/api/finance/expenses")
@PreAuthorize("hasRole('ADMIN')")
public class ExpenseController {

    /** Read as an optional header, like billing's: the domain refuses it absent on a cash payment. */
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final ExpenseService expenses;
    private final Clock clock;

    public ExpenseController(ExpenseService expenses, Clock clock) {
        this.expenses = expenses;
        this.clock = clock;
    }

    /**
     * Registers money going out. With {@code method}, it is paid now; without it, it is recorded as
     * owed and shows up in the accounts payable.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse register(
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody ExpenseRequest request) {
        RegisterExpenseCommand command = new RegisterExpenseCommand(
                request.getCategory(),
                request.getDescription(),
                request.getAmount() == null ? null : Money.of(request.getAmount()),
                dateOrToday(request.getAccrualDate()),
                date(request.getDueDate()),
                request.getSupplierName(),
                uuid(request.getEmployeeId()),
                request.getMethod(),
                idempotencyKey);
        return respond(expenses.register(command));
    }

    /** Settles an account payable. */
    @PostMapping("/{expenseId}/payment")
    public ExpenseResponse pay(
            @PathVariable("expenseId") String expenseId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody ExpensePaymentRequest request) {
        return respond(expenses.pay(ExpenseId.of(expenseId), request.getMethod(), idempotencyKey));
    }

    /** Cancels an expense registered by mistake. It stays on the books, cancelled, with the reason. */
    @PostMapping("/{expenseId}/cancel")
    public ExpenseResponse cancel(
            @PathVariable("expenseId") String expenseId, @RequestBody ExpenseCancellationRequest request) {
        return respond(expenses.cancel(ExpenseId.of(expenseId), request.getReason()));
    }

    @GetMapping("/{expenseId}")
    public ExpenseResponse find(@PathVariable("expenseId") String expenseId) {
        return respond(expenses.find(ExpenseId.of(expenseId)));
    }

    /**
     * The expenses of a period. {@code by=ACCRUAL} (the default) lists them by the month they belong
     * to, which is what the result of the period counts; {@code by=PAYMENT} lists them by the day
     * the money left, which is what the cash flow counts.
     */
    @GetMapping
    public ExpenseListResponse list(
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(name = "by", required = false, defaultValue = "ACCRUAL") String by) {
        LocalDate start = LocalDate.parse(from);
        LocalDate end = LocalDate.parse(to);
        return ExpenseListResponse.from(
                "PAYMENT".equalsIgnoreCase(by)
                        ? expenses.paidBetween(start, end)
                        : expenses.accruedBetween(start, end),
                today());
    }


    private ExpenseResponse respond(Expense expense) {
        return ExpenseResponse.from(expense, today());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneId.systemDefault()));
    }

    private LocalDate dateOrToday(String value) {
        return value == null || value.isBlank() ? today() : LocalDate.parse(value);
    }

    private static LocalDate date(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private static UUID uuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
