package br.com.castel.finance.web;

import br.com.castel.finance.application.ExpenseService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the business owes and has not paid — an expense whose {@code paidAt} is empty.
 *
 * <p>A route of its own, and not a filter on the expense list, because it answers a different
 * question: not "what did October cost" but "what do I have to pay, and when". It is the list the
 * owner opens on Monday morning, ordered by what is closest to its due date, with what is already
 * overdue at the top.
 */
@RestController
@RequestMapping("/api/finance/payables")
@PreAuthorize("hasRole('ADMIN')")
public class PayableController {

    private final ExpenseService expenses;
    private final Clock clock;

    public PayableController(ExpenseService expenses, Clock clock) {
        this.expenses = expenses;
        this.clock = clock;
    }

    /**
     * @param until only what is due up to this date; absent brings everything owed, including the
     *     expenses with no due date, which come last
     */
    @GetMapping
    public ExpenseListResponse list(@RequestParam(name = "until", required = false) String until) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.systemDefault()));
        LocalDate limit = until == null || until.isBlank() ? null : LocalDate.parse(until);
        return ExpenseListResponse.from(expenses.payable(limit), today);
    }
}
