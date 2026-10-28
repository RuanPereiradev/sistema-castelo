package br.com.castel.restaurant.application;

import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.DiningTableNotFoundException;
import br.com.castel.restaurant.domain.DiningTableRepository;
import br.com.castel.restaurant.domain.MenuItemRepository;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabOrigin;
import br.com.castel.restaurant.domain.TabRepository;
import br.com.castel.restaurant.domain.TableOrderRequest;
import br.com.castel.restaurant.domain.TableOrderRequestId;
import br.com.castel.restaurant.domain.TableOrderRequestLine;
import br.com.castel.restaurant.domain.TableOrderRequestNotFoundException;
import br.com.castel.restaurant.domain.TableOrderRequestRepository;
import br.com.castel.restaurant.domain.TableOrderRequestStatusChanged;
import br.com.castel.sharedkernel.AuditorAware;
import br.com.castel.sharedkernel.CurrentProperty;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The orders a customer sends from the QR code of a table, and the waiter answering them (task
 * qr-table-ordering).
 *
 * <p>Only orchestration: the rules live in {@link TableOrderRequest}, and confirming goes through
 * {@link TabService#open} and {@link TabService#addItem} in the same transaction, so every rule of
 * ordering applies without a copy (decision #19), the tab keeps its locks ({@code FOR KEY SHARE} per
 * line) and each item reaches the kitchen display by the same {@code TabItemOrdered}. A refusal of
 * any of them rolls back everything, the opened tab included, and the request stays {@code PENDING}
 * (decision #21).
 *
 * <p>A request past its window is refused by the waiter's routes without being written (decision
 * #29); {@link #expireOverdue} writes it, run every minute by {@link TableOrderRequestExpiryJob}.
 * The submission writes the overdue request of its own table first, since the unique index would
 * otherwise refuse the new one.
 *
 * <p>Every change publishes {@link TableOrderRequestStatusChanged}, which the floor hears after the
 * commit (decision #25).
 */
@Service
public class TableOrderRequestService {

    private final TableOrderRequestRepository requests;
    private final DiningTableRepository diningTables;
    private final TabRepository tabRepository;
    private final MenuItemRepository menuItems;
    private final TabService tabs;
    private final CurrentProperty currentProperty;
    private final AuditorAware auditorAware;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public TableOrderRequestService(
            TableOrderRequestRepository requests,
            DiningTableRepository diningTables,
            TabRepository tabRepository,
            MenuItemRepository menuItems,
            TabService tabs,
            CurrentProperty currentProperty,
            AuditorAware auditorAware,
            Clock clock,
            ApplicationEventPublisher events) {
        this.requests = requests;
        this.diningTables = diningTables;
        this.tabRepository = tabRepository;
        this.menuItems = menuItems;
        this.tabs = tabs;
        this.currentProperty = currentProperty;
        this.auditorAware = auditorAware;
        this.clock = clock;
        this.events = events;
    }

    // ------------------------------------------------------------------ the customer

    /**
     * The table as the phone reads it: whether it takes an order, how many requests wait on it, and
     * the one of the device reading, if any (decision #66 of task qr-table-payment).
     *
     * @param customerDeviceId the device reading; empty when the phone did not say
     * @throws DiningTableNotFoundException if the code is unknown, regenerated or of a deactivated table
     */
    @Transactional(readOnly = true)
    public TableOrderingView viewTable(UUID orderingToken, Optional<String> customerDeviceId) {
        DiningTable diningTable = loadTable(orderingToken);
        Instant now = now();
        int pendingOnTable = requests.countOpenOnDiningTable(diningTable.id(), now);
        return new TableOrderingView(
                diningTable,
                TableOrderRequest.acceptsOrdersWithTabIn(
                        tabRepository.findActiveStatusOnDiningTable(diningTable.id()), pendingOnTable),
                customerDeviceId
                        .flatMap(device -> requests.findOpenOnDiningTableForDevice(diningTable.id(), device, now))
                        .map(TableOrderRequest::id),
                pendingOnTable);
    }

    /**
     * Sends a request from the QR code of the table.
     *
     * @throws DiningTableNotFoundException if the code is unknown, regenerated or of a deactivated table
     * <p>The row of the table is locked first, so two phones submitting at once count the requests
     * waiting one after the other; the overdue ones are expired, and only then the device and the cap
     * of the table are checked (decisions #62, #64 and #65 of task qr-table-payment).
     *
     * @see TableOrderRequest#requireRoomOnTable for the refusals of the table
     * @see TableOrderRequest#submit for the refusals of the request itself
     */
    @Transactional
    public TableOrderRequest submit(UUID orderingToken, String customerName, String customerDeviceId,
            List<TableOrderRequestLine> lines) {
        DiningTable diningTable = loadTable(orderingToken);
        Instant now = now();
        diningTables.lockForOrdering(diningTable.id());
        requests.findOverdueOnDiningTableForUpdate(diningTable.id(), now).forEach(overdue -> expire(overdue, now));
        TableOrderRequest.requireRoomOnTable(
                requests.findOpenOnDiningTableForDevice(diningTable.id(), customerDeviceId, now).isPresent(),
                requests.countOpenOnDiningTable(diningTable.id(), now));
        TableOrderRequest request = TableOrderRequest.submit(currentProperty.id(), diningTable, customerName,
                customerDeviceId, activeTabOn(diningTable.id()), lines, menuItems, now, currentProperty.timeZone());
        TableOrderRequest saved = requests.add(request);
        events.publishEvent(TableOrderRequestStatusChanged.of(saved, now));
        return saved;
    }

    /**
     * The request as the phone that sent it reads it.
     *
     * @throws DiningTableNotFoundException if the code is unknown, regenerated or of a deactivated table
     * @throws TableOrderRequestNotFoundException if the id is unknown or of another table
     */
    @Transactional(readOnly = true)
    public TableOrderRequest findForTable(UUID orderingToken, TableOrderRequestId requestId) {
        DiningTable diningTable = loadTable(orderingToken);
        return requests.findByIdOnDiningTable(requestId, diningTable.id()).orElseThrow(() -> notFound(requestId));
    }

    // ------------------------------------------------------------------ the floor

    /** The requests waiting for a waiter, oldest first, leaving out the ones past their window. */
    @Transactional(readOnly = true)
    public List<TableOrderRequest> listPending() {
        return requests.findOpen(currentProperty.id(), now());
    }

    /**
     * Puts every line on the tab of the table, opening one when the table has none, as the waiter
     * authenticated now (decisions #7, #8 and #9).
     *
     * @throws TableOrderRequestNotFoundException if the id is unknown
     * @throws br.com.castel.restaurant.domain.TableOrderRequestNotPendingException if it was already answered
     * @throws br.com.castel.restaurant.domain.TableOrderRequestExpiredException if its window is over
     * @see TabService#open and TabService#addItem for the refusals of the ordering itself
     */
    @Transactional
    public TableOrderRequest confirm(TableOrderRequestId requestId) {
        TableOrderRequest request = loadForAnswer(requestId);
        Instant now = now();
        request.requireAnswerableAt(now);
        TabId tabId = tabRepository.findActiveIdOnDiningTable(request.diningTableId())
                .orElseGet(() -> tabs.open(TabOrigin.TABLE_SERVICE, request.diningTableId(), null).id());
        request.lines().forEach(line -> tabs.addItem(tabId, commandFrom(line, request)));
        request.confirm(auditorAware.currentAuditorId(), now, tabId);
        TableOrderRequest saved = requests.save(request);
        events.publishEvent(TableOrderRequestStatusChanged.of(saved, now));
        return saved;
    }

    /**
     * @throws TableOrderRequestNotFoundException if the id is unknown
     * @throws br.com.castel.restaurant.domain.TableOrderRequestNotPendingException if it was already answered
     * @throws br.com.castel.restaurant.domain.TableOrderRequestExpiredException if its window is over
     */
    @Transactional
    public TableOrderRequest reject(TableOrderRequestId requestId) {
        TableOrderRequest request = loadForAnswer(requestId);
        Instant now = now();
        request.reject(auditorAware.currentAuditorId(), now);
        TableOrderRequest saved = requests.save(request);
        events.publishEvent(TableOrderRequestStatusChanged.of(saved, now));
        return saved;
    }

    /** Writes {@code EXPIRED} on every request past its window (decision #22). */
    @Transactional
    public void expireOverdue() {
        Instant now = now();
        requests.findOverdueForUpdate(currentProperty.id(), now).forEach(overdue -> expire(overdue, now));
    }

    // ------------------------------------------------------------------ internals

    private void expire(TableOrderRequest overdue, Instant now) {
        overdue.expire(now);
        TableOrderRequest saved = requests.save(overdue);
        events.publishEvent(TableOrderRequestStatusChanged.of(saved, now));
    }

    /**
     * The clock truncated to microseconds, the precision of {@code timestamptz}: a moment written and
     * a moment compared then agree at the border of the window (decision #39).
     */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private DiningTable loadTable(UUID orderingToken) {
        return diningTables.findActiveByOrderingToken(orderingToken)
                .orElseThrow(() -> new DiningTableNotFoundException("No dining table answers this ordering code"));
    }

    private TableOrderRequest loadForAnswer(TableOrderRequestId requestId) {
        return requests.findByIdForUpdate(requestId).orElseThrow(() -> notFound(requestId));
    }

    /**
     * The {@code OPEN} or {@code CLOSING} tab of the table; at most one, by {@code idx_tab_open_by_table}.
     * Only for reading: confirming reads the id alone, so that {@link TabService#addItem} loads the
     * tab fresh under its lock instead of finding a copy read before it.
     */
    private Optional<Tab> activeTabOn(DiningTableId diningTableId) {
        return tabRepository.findActive(currentProperty.id(), diningTableId, null).stream().findFirst();
    }

    /**
     * The line as the waiter would have ordered it, with the customer who asked for it (decision #40 of
     * task qr-table-payment); a customer never sends a weight (decision #20).
     */
    private static AddTabItemCommand commandFrom(TableOrderRequestLine line, TableOrderRequest request) {
        return new AddTabItemCommand(
                line.menuItemId(),
                line.variantId().orElse(null),
                line.quantity().orElse(null),
                null,
                line.modifiers().stream()
                        .map(chosen -> new AddTabItemCommand.ChosenModifier(chosen.modifierId(), chosen.quantity()))
                        .toList(),
                line.specialInstructions().orElse(null),
                request.customerName().orElse(null),
                request.customerDeviceId().orElse(null));
    }

    private static TableOrderRequestNotFoundException notFound(TableOrderRequestId requestId) {
        return new TableOrderRequestNotFoundException("No table order request " + requestId.value());
    }
}
