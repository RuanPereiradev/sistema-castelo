package br.com.castel.finance.infra;

import br.com.castel.finance.domain.Expense;
import br.com.castel.finance.domain.ExpenseId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data's view of {@code expense}, used only by {@link JpaExpenseRepository}. */
interface SpringDataExpenseRepository extends JpaRepository<Expense, ExpenseId> {

    /**
     * Locks the row {@code FOR UPDATE} and answers its id, so two operators paying the same
     * account payable run one after the other. Native because JPQL cannot lock and the aggregate
     * is loaded afterwards, the way the rest of the system does it.
     */
    @Query(value = "select cast(id as varchar) from expense where id = :id for update", nativeQuery = true)
    Optional<String> lockForUpdate(@Param("id") UUID id);

    @Query("select e from Expense e where e.propertyId = :propertyId "
            + "and e.accrualDate between :from and :to and e.cancelledAt is null "
            + "order by e.accrualDate desc, e.id")
    List<Expense> findByAccrualPeriod(
            @Param("propertyId") UUID propertyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("select e from Expense e where e.propertyId = :propertyId "
            + "and e.paidAt >= :from and e.paidAt < :to and e.cancelledAt is null "
            + "order by e.paidAt desc, e.id")
    List<Expense> findByPaymentPeriod(
            @Param("propertyId") UUID propertyId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** Nulls last, so an expense without a due date does not jump ahead of what is overdue. */
    @Query("select e from Expense e where e.propertyId = :propertyId "
            + "and e.paidAt is null and e.cancelledAt is null "
            + "and (:until is null or (e.dueDate is not null and e.dueDate <= :until)) "
            + "order by e.dueDate asc nulls last, e.id")
    List<Expense> findPayable(@Param("propertyId") UUID propertyId, @Param("until") LocalDate until);
}
