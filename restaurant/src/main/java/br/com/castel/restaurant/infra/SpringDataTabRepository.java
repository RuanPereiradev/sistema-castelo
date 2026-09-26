package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabId;
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
}
