package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data's view of {@code tab}, used only by {@link JpaTabRepository}. */
interface SpringDataTabRepository extends JpaRepository<Tab, TabId> {

    /**
     * Locks the row of the tab {@code FOR KEY SHARE} and answers its id. Native because JPQL has no
     * lock mode for it: {@code PESSIMISTIC_READ} is {@code FOR SHARE}, which the {@code UPDATE} of a
     * cancellation would conflict with.
     */
    @Query(value = "select cast(id as varchar) from tab where id = :id for key share", nativeQuery = true)
    Optional<String> lockForKeyShare(@Param("id") UUID id);

    /** Locks the row of the tab {@code FOR UPDATE}, which conflicts with {@code FOR KEY SHARE}. */
    @Query(value = "select cast(id as varchar) from tab where id = :id for update", nativeQuery = true)
    Optional<String> lockForUpdate(@Param("id") UUID id);

    /**
     * Locks the row of one item of the tab {@code FOR UPDATE}, so two cancellations of the same item
     * run one after the other. Answers nothing when the item is not on that tab.
     */
    @Query(value = "select cast(id as varchar) from tab_item where id = :itemId and tab_id = :tabId for update",
            nativeQuery = true)
    Optional<String> lockItemForUpdate(@Param("tabId") UUID tabId, @Param("itemId") UUID itemId);

    @Query("select t from Tab t where t.propertyId = :propertyId "
            + "and t.status in (br.com.castel.restaurant.domain.TabStatus.OPEN, "
            + "br.com.castel.restaurant.domain.TabStatus.CLOSING) "
            + "and (:diningTableId is null or t.diningTableId.value = :diningTableId) "
            + "and (:cardNumber is null or t.cardNumber = :cardNumber) "
            + "order by t.openedAt, t.id")
    List<Tab> findActive(
            @Param("propertyId") UUID propertyId,
            @Param("diningTableId") UUID diningTableId,
            @Param("cardNumber") Integer cardNumber);

    @Query("select count(t) > 0 from Tab t where t.diningTableId = :diningTableId "
            + "and t.status in (br.com.castel.restaurant.domain.TabStatus.OPEN, "
            + "br.com.castel.restaurant.domain.TabStatus.CLOSING)")
    boolean existsActiveOnDiningTable(@Param("diningTableId") DiningTableId diningTableId);

    // ---- kitchen display

    /** The id of the tab holding the item, read without any lock. */
    @Query(value = "select cast(tab_id as varchar) from tab_item where id = :itemId", nativeQuery = true)
    Optional<String> findTabIdOfItem(@Param("itemId") UUID itemId);

    // ---- activity of the floor, for the finance module (task F1)

    /**
     * How much the floor moved between the two instants: tabs opened, how many of them informed the
     * number of guests, the sum of those guests, and the split between table and card.
     *
     * <p>{@code CANCELLED} and {@code MERGED} are left out: a tab opened by mistake and a tab
     * absorbed by another never served anybody, and counting them would inflate the movement of
     * customers.
     *
     * <p>One query for the five figures, so they stay consistent with each other even if a tab is
     * opened while the report is being built. Native because {@code count(... ) filter (where ...)}
     * is PostgreSQL's, and JPQL has no equivalent that reads as clearly.
     */
    @Query(value = "select count(*), "
            + "count(guest_count), "
            + "coalesce(sum(guest_count), 0), "
            + "count(*) filter (where origin = 'TABLE_SERVICE'), "
            + "count(*) filter (where origin = 'SELF_SERVICE') "
            + "from tab "
            + "where property_id = :propertyId "
            + "and opened_at >= :from "
            + "and opened_at < :to "
            + "and status not in ('CANCELLED', 'MERGED')", nativeQuery = true)
    Object[] activityBetween(
            @Param("propertyId") UUID propertyId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
