package br.com.castel.restaurant.infra;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.restaurant.application.KitchenQueue;
import br.com.castel.restaurant.application.KitchenTicket;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.restaurant.domain.TabItemStatus;
import br.com.castel.restaurant.domain.TabOrigin;
import jakarta.persistence.EntityManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Answers the {@link KitchenQueue} port with two plain SQL reads: the items joined to their tab and
 * dining table, over {@code idx_kds_queue}, then their modifiers. No aggregate is loaded and nothing
 * is locked: reading the queue never waits for a waiter or for the kitchen.
 *
 * <p>Each read flushes the persistence context first, the way Hibernate flushes before its own
 * queries: a transition answers the ticket it just wrote, read in the same transaction, and a
 * violation of that write surfaces from the flush with its own cause, before any read runs.
 */
@Repository
class JpaKitchenQueue implements KitchenQueue {

    private static final String TICKET_COLUMNS = """
            select ti.id, ti.tab_id, t.origin, dt.label as dining_table_label, t.card_number,
                   ti.item_name, ti.variant_name, ti.quantity, ti.special_instructions, ti.prep_station,
                   ti.status, ti.ordered_at, ti.preparation_started_at, ti.ready_at, ti.updated_at
              from tab_item ti
              join tab t on t.id = ti.tab_id
              left join dining_table dt on dt.id = t.dining_table_id
            """;

    private static final String QUEUE_OF_STATION = TICKET_COLUMNS + """
             where t.property_id = :propertyId
               and ti.prep_station = :station
               and ti.status in ('PENDING', 'IN_PREPARATION', 'READY')
             order by ti.ordered_at, ti.id
            """;

    private static final String ONE_TICKET = TICKET_COLUMNS + " where ti.id = :itemId";

    private static final String MODIFIERS_OF_ITEMS = """
            select tab_item_id, modifier_name, quantity
              from tab_item_modifier
             where tab_item_id in (:itemIds)
             order by modifier_name
            """;

    private final JdbcClient jdbcClient;
    private final EntityManager entityManager;

    JpaKitchenQueue(JdbcClient jdbcClient, EntityManager entityManager) {
        this.jdbcClient = jdbcClient;
        this.entityManager = entityManager;
    }

    @Override
    public List<KitchenTicket> ticketsOf(UUID propertyId, PrepStation station) {
        flushPendingChanges();
        List<TicketRow> rows = jdbcClient.sql(QUEUE_OF_STATION)
                .param("propertyId", propertyId)
                .param("station", station.name())
                .query(JpaKitchenQueue::ticketRow)
                .list();
        return withModifiers(rows);
    }

    @Override
    public Optional<KitchenTicket> ticket(TabItemId itemId) {
        flushPendingChanges();
        List<TicketRow> rows = jdbcClient.sql(ONE_TICKET)
                .param("itemId", itemId.value())
                .query(JpaKitchenQueue::ticketRow)
                .list();
        return withModifiers(rows).stream().findFirst();
    }

    private void flushPendingChanges() {
        if (entityManager.isJoinedToTransaction()) {
            entityManager.flush();
        }
    }

    private List<KitchenTicket> withModifiers(List<TicketRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<KitchenTicket.ModifierLine>> modifiersByItem = jdbcClient.sql(MODIFIERS_OF_ITEMS)
                .param("itemIds", rows.stream().map(TicketRow::itemId).toList())
                .query((row, number) -> new ModifierRow(
                        row.getObject("tab_item_id", UUID.class),
                        new KitchenTicket.ModifierLine(row.getString("modifier_name"), row.getInt("quantity"))))
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        ModifierRow::itemId, Collectors.mapping(ModifierRow::line, Collectors.toList())));
        return rows.stream()
                .map(row -> row.toTicket(modifiersByItem.getOrDefault(row.itemId(), List.of())))
                .toList();
    }

    private static TicketRow ticketRow(ResultSet row, int number) throws SQLException {
        return new TicketRow(
                row.getObject("id", UUID.class),
                row.getObject("tab_id", UUID.class),
                TabOrigin.valueOf(row.getString("origin")),
                row.getString("dining_table_label"),
                (Integer) row.getObject("card_number"),
                row.getString("item_name"),
                row.getString("variant_name"),
                row.getInt("quantity"),
                row.getString("special_instructions"),
                PrepStation.valueOf(row.getString("prep_station")),
                TabItemStatus.valueOf(row.getString("status")),
                instantOf(row, "ordered_at"),
                instantOf(row, "preparation_started_at"),
                instantOf(row, "ready_at"),
                instantOf(row, "updated_at"));
    }

    private static Instant instantOf(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /** One modifier of one item, in the order of the second read. */
    private record ModifierRow(UUID itemId, KitchenTicket.ModifierLine line) {}

    /** One row of the first read, waiting for its modifiers. */
    private record TicketRow(
            UUID itemId,
            UUID tabId,
            TabOrigin origin,
            String diningTableLabel,
            Integer cardNumber,
            String itemName,
            String variantName,
            int quantity,
            String specialInstructions,
            PrepStation prepStation,
            TabItemStatus status,
            Instant orderedAt,
            Instant preparationStartedAt,
            Instant readyAt,
            Instant updatedAt) {

        KitchenTicket toTicket(List<KitchenTicket.ModifierLine> modifiers) {
            return new KitchenTicket(
                    new TabItemId(itemId), new TabId(tabId), origin, diningTableLabel, cardNumber,
                    itemName, variantName, quantity, modifiers, specialInstructions, prepStation, status,
                    orderedAt, preparationStartedAt, readyAt, updatedAt);
        }
    }
}
