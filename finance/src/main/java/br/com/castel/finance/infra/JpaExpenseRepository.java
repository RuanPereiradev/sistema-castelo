package br.com.castel.finance.infra;

import br.com.castel.finance.domain.Expense;
import br.com.castel.finance.domain.ExpenseId;
import br.com.castel.finance.domain.ExpenseRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Answers the {@link ExpenseRepository} port over Spring Data JPA.
 *
 * <p>The two period reads are deliberately different: the result of a period counts expenses by the
 * month they belong to, and the cash flow counts them by the day the money left (decision D1).
 */
@Repository
class JpaExpenseRepository implements ExpenseRepository {

    private final SpringDataExpenseRepository springData;

    JpaExpenseRepository(SpringDataExpenseRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<Expense> findById(ExpenseId id) {
        return springData.findById(id);
    }

    /** Locks first, then loads, as the rest of the system does. */
    @Override
    public Optional<Expense> findByIdForUpdate(ExpenseId id) {
        return springData.lockForUpdate(id.value()).flatMap(locked -> springData.findById(id));
    }

    @Override
    public Expense save(Expense expense) {
        return springData.save(expense);
    }

    @Override
    public List<Expense> findByAccrualPeriod(UUID propertyId, LocalDate from, LocalDate to) {
        return springData.findByAccrualPeriod(propertyId, from, to);
    }

    /** The dates come as whole days; the payment is an instant, so the end is exclusive. */
    @Override
    public List<Expense> findByPaymentPeriod(UUID propertyId, LocalDate from, LocalDate to) {
        return springData.findByPaymentPeriod(
                propertyId,
                from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    @Override
    public List<Expense> findPayable(UUID propertyId, LocalDate until) {
        return springData.findPayable(propertyId, until);
    }
}
