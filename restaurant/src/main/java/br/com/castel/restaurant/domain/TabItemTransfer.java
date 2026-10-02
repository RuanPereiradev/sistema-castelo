package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.AuditedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One movement of one item between two tabs: the trail (decision T11).
 *
 * <p>Append-only. A row is written when the item moves and is never changed nor removed, not even
 * when a tab is cancelled, which is what lets it answer "why did table 4 close with less" after any
 * number of hops. {@code TabItem.transferredFromTabId} answers the same question for the
 * <em>last</em> hop only, and is overwritten on every move: it is the shortcut the screen and the
 * pre-bill read without a join, not the record.
 *
 * <p>Not part of the {@link Tab} aggregate: a movement belongs to two tabs at once, so it could not
 * live inside either. The aggregate builds the rows and hands them back; the use case stores them
 * through {@link TabItemTransferRepository}, in the same transaction as the move.
 */
@Entity
@Table(name = "tab_item_transfer")
public class TabItemTransfer extends AuditedEntity {

    @EmbeddedId
    @AttributeOverride(name = "value", column = @Column(name = "id"))
    private TabItemTransferId id;

    @AttributeOverride(name = "value", column = @Column(name = "tab_item_id", nullable = false, updatable = false))
    private TabItemId tabItemId;

    @AttributeOverride(name = "value", column = @Column(name = "from_tab_id", nullable = false, updatable = false))
    private TabId fromTabId;

    @AttributeOverride(name = "value", column = @Column(name = "to_tab_id", nullable = false, updatable = false))
    private TabId toTabId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private TabTransferKind kind;

    @Column(name = "transferred_by", nullable = false, updatable = false)
    private UUID transferredBy;

    @Column(name = "transferred_at", nullable = false, updatable = false)
    private Instant transferredAt;

    protected TabItemTransfer() {
        // for JPA
    }

    /**
     * Records that the item left one tab for another. Receives values the {@link Tab} already
     * checked, the two tabs being distinct among them.
     */
    static TabItemTransfer record(
            TabItemId tabItemId, TabId fromTabId, TabId toTabId, TabTransferKind kind, UUID by, Instant at) {
        TabItemTransfer transfer = new TabItemTransfer();
        transfer.id = TabItemTransferId.newId();
        transfer.tabItemId = Objects.requireNonNull(tabItemId, "tabItemId");
        transfer.fromTabId = Objects.requireNonNull(fromTabId, "fromTabId");
        transfer.toTabId = Objects.requireNonNull(toTabId, "toTabId");
        transfer.kind = Objects.requireNonNull(kind, "kind");
        transfer.transferredBy = Objects.requireNonNull(by, "by");
        transfer.transferredAt = Objects.requireNonNull(at, "at");
        return transfer;
    }

    public TabItemTransferId id() {
        return id;
    }

    public TabItemId tabItemId() {
        return tabItemId;
    }

    public TabId fromTabId() {
        return fromTabId;
    }

    public TabId toTabId() {
        return toTabId;
    }

    public TabTransferKind kind() {
        return kind;
    }

    public UUID transferredBy() {
        return transferredBy;
    }

    public Instant transferredAt() {
        return transferredAt;
    }
}
