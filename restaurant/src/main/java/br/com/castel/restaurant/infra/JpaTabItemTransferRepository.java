package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.domain.TabItemTransfer;
import br.com.castel.restaurant.domain.TabItemTransferRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Answers the {@link TabItemTransferRepository} port over Spring Data JPA.
 *
 * <p>Nothing here locks: the rows are inserted in the transaction that moved the items, which
 * already holds the locks of the two tabs and of each item. The insert itself only needs
 * {@code FOR KEY SHARE} on the rows its foreign keys point at, which the caller holds in a
 * compatible mode.
 */
@Repository
class JpaTabItemTransferRepository implements TabItemTransferRepository {

    private final SpringDataTabItemTransferRepository springData;

    JpaTabItemTransferRepository(SpringDataTabItemTransferRepository springData) {
        this.springData = springData;
    }

    @Override
    public void saveAll(List<TabItemTransfer> transfers) {
        springData.saveAll(transfers);
    }
}
