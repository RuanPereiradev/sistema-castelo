package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.TabItemTransfer;
import br.com.castel.restaurant.domain.TabItemTransferId;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data's view of {@code tab_item_transfer}, used only by {@link JpaTabItemTransferRepository}. */
interface SpringDataTabItemTransferRepository extends JpaRepository<TabItemTransfer, TabItemTransferId> {
}
