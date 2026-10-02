package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.restaurant.domain.TabItemTransfer;
import br.com.castel.restaurant.domain.TabItemTransferId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data's view of {@code tab_item_transfer}, used only by {@link JpaTabItemTransferRepository}. */
interface SpringDataTabItemTransferRepository extends JpaRepository<TabItemTransfer, TabItemTransferId> {

    @Query("select t from TabItemTransfer t where t.tabItemId = :itemId order by t.transferredAt, t.id")
    List<TabItemTransfer> findByTabItemId(@Param("itemId") TabItemId itemId);
}
