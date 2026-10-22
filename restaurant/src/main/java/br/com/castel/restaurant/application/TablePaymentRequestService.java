package br.com.castel.restaurant.application;

import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.DiningTableNotFoundException;
import br.com.castel.restaurant.domain.DiningTableRepository;
import br.com.castel.restaurant.domain.PixReceiver;
import br.com.castel.restaurant.domain.PixReceiverRepository;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabBilling;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabNotFoundException;
import br.com.castel.restaurant.domain.TabRepository;
import br.com.castel.restaurant.domain.TablePaymentRequest;
import br.com.castel.restaurant.domain.TablePaymentRequestId;
import br.com.castel.restaurant.domain.TablePaymentRequestNotFoundException;
import br.com.castel.restaurant.domain.TablePaymentRequestRepository;
import br.com.castel.restaurant.domain.TablePaymentRequestStatusChanged;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The payments a table sends from its phone by Pix, and the waiter answering them (task
 * qr-table-payment).
 *
 * <p>Only orchestration: the rules live in {@link TablePaymentRequest} and {@link Tab}, which talks to
 * billing through the {@link TabBilling} port in this same transaction.
 *
 * <p>Locks (decision #27), always in the order request → tab → folio: every write locks the tab
 * {@code FOR UPDATE}, so two phones choosing the same item queue and the second is refused, and a
 * confirmation queues with the closing, a cancellation of an item and an ordering. The waiter's
 * answers lock the request first, so two waiters answering it queue and the second finds it answered.
 *
 * <p>A request past its window is refused without being written; the expiry job writes it, frees its
 * items and tells the floor (decision #34). Every change publishes
 * {@link TablePaymentRequestStatusChanged}, which the floor hears after the commit.
 */
@Service
public class TablePaymentRequestService {

    private final TablePaymentRequestRepository requests;
    private final DiningTableRepository diningTables;
    private final TabRepository tabs;
    private final PixReceiverRepository pixReceivers;
    private final TabBilling billing;
    private final TabClosingService closing;
    private final CurrentProperty currentProperty;
    private final AuditorAware auditorAware;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;

    public TablePaymentRequestService(
            TablePaymentRequestRepository requests,
            DiningTableRepository diningTables,
            TabRepository tabs,
            PixReceiverRepository pixReceivers,
            TabBilling billing,
            TabClosingService closing,
            CurrentProperty currentProperty,
            AuditorAware auditorAware,
            Clock clock,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.requests = requests;
        this.diningTables = diningTables;
        this.tabs = tabs;
        this.pixReceivers = pixReceivers;
        this.billing = billing;
        this.closing = closing;
        this.currentProperty = currentProperty;
        this.auditorAware = auditorAware;
        this.clock = clock;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ the phone

    /**
     * The active tab of the table as the phone reads it (decision #1).
     *
     * @throws DiningTableNotFoundException if the code is unknown, regenerated or of a deactivated table
     * @throws TabNotFoundException if the table has no {@code OPEN} or {@code CLOSING} tab
     */
    @Transactional(readOnly = true)
    public TablePaymentTabView viewTab(UUID orderingToken, Optional<String> customerDeviceId) {
        DiningTable diningTable = loadTable(orderingToken);
        Tab tab = tabs.findById(activeTabIdOf(diningTable)).orElseThrow(() -> noActiveTab());
        return new TablePaymentTabView(diningTable, tab, closing.currentServiceChargeRate(),
                pixReceivers.find().isPresent(), customerDeviceId);
    }

    /**
     * Sends a payment from the phone for the items chosen.
     *
     * <p>The overdue requests of the tab are expired first, freeing their units (decision #52), and
     * a tab closed or merged between the read of its id and its lock answers like a table without a
     * tab, with no id in the detail (decision #51).
     *
     * @throws DiningTableNotFoundException if the code is unknown, regenerated or of a deactivated table
     * @throws TabNotFoundException if the table has no {@code OPEN} or {@code CLOSING} tab
     * @see TablePaymentRequest#request for the refusals of the request
     */
    @Transactional
    public TablePaymentRequest submit(UUID orderingToken, String customerName, String customerDeviceId,
            List<Tab.PartChoice> choices, boolean includeServiceCharge) {
        DiningTable diningTable = loadTable(orderingToken);
        Instant now = now();
        TabId tabId = activeTabIdOf(diningTable);
        List<TablePaymentRequest> overdue = requests.findOverdueOnTabForUpdate(tabId, now);
        Tab tab = tabs.findByIdForStatusChange(tabId)
                .filter(locked -> locked.status().acceptsTablePayment())
                .orElseThrow(() -> noActiveTab());
        overdue.forEach(request -> expireLocked(request, tab, now));
        TablePaymentRequest request = TablePaymentRequest.request(currentProperty.id(), tab, customerName,
                customerDeviceId, choices, includeServiceCharge, closing.currentServiceChargeRate(),
                pixReceivers.find(), now);
        TablePaymentRequest saved = requests.add(request);
        tabs.save(tab);
        events.publishEvent(TablePaymentRequestStatusChanged.of(saved, now));
        return saved;
    }

    /**
     * The request as the phone that sent it reads it.
     *
     * @throws DiningTableNotFoundException if the code is unknown, regenerated or of a deactivated table
     * @throws TablePaymentRequestNotFoundException if the id is unknown or of another table
     */
    @Transactional(readOnly = true)
    public TablePaymentRequest findForTable(UUID orderingToken, TablePaymentRequestId requestId) {
        DiningTable diningTable = loadTable(orderingToken);
        return requests.findByIdOnDiningTable(requestId, diningTable.id()).orElseThrow(() -> notFound(requestId));
    }

    // ------------------------------------------------------------------ the floor

    /** The requests waiting for the waiter, oldest first, leaving out the ones past their window. */
    @Transactional(readOnly = true)
    public List<TablePaymentRequest> listPending() {
        return requests.findOpen(currentProperty.id(), now());
    }

    /**
     * @throws TablePaymentRequestNotFoundException if the id is unknown
     * @see TablePaymentRequest#confirm for the refusals
     */
    @Transactional
    public TablePaymentRequest confirm(TablePaymentRequestId requestId) {
        TablePaymentRequest request = loadForAnswer(requestId);
        Instant now = now();
        request.requireAnswerableAt(now);
        Tab tab = loadTabForUpdate(request.tabId());
        request.confirm(tab, billing, auditorAware.currentAuditorId(), now);
        return saveAndPublish(request, tab, now);
    }

    /**
     * @throws TablePaymentRequestNotFoundException if the id is unknown
     * @see TablePaymentRequest#reject for the refusals
     */
    @Transactional
    public TablePaymentRequest reject(TablePaymentRequestId requestId) {
        TablePaymentRequest request = loadForAnswer(requestId);
        Instant now = now();
        request.requireAnswerableAt(now);
        Tab tab = loadTabForUpdate(request.tabId());
        request.reject(tab, auditorAware.currentAuditorId(), now);
        return saveAndPublish(request, tab, now);
    }

    /**
     * @throws TablePaymentRequestNotFoundException if the id is unknown
     * @see TablePaymentRequest#refund for the refusals
     */
    @Transactional
    public TablePaymentRequest refund(TablePaymentRequestId requestId, String reason) {
        TablePaymentRequest request = loadForAnswer(requestId);
        Instant now = now();
        Tab tab = loadTabForUpdate(request.tabId());
        request.refund(tab, reason, billing, auditorAware.currentAuditorId(), now);
        return saveAndPublish(request, tab, now);
    }

    /**
     * Writes {@code EXPIRED} on every request past its window and frees its units (decision #34), one
     * transaction per request (decision #50): each locks its request and then its tab, the order every
     * other path takes, so the job never holds the locks of two tabs at once in an order of its own. A
     * request answered meanwhile is left as it is.
     */
    public void expireOverdue() {
        Instant now = now();
        for (TablePaymentRequestId overdueId : requests.findOverdueIds(currentProperty.id(), now)) {
            transactions.executeWithoutResult(status -> requests.findByIdForUpdate(overdueId)
                    .filter(request -> request.isExpiredAt(now))
                    .ifPresent(request -> expireLocked(request, loadTabForUpdate(request.tabId()), now)));
        }
    }

    // ------------------------------------------------------------------ the administrator

    /** The Pix receiver, or empty while it is not configured (decision #33). */
    @Transactional(readOnly = true)
    public Optional<PixReceiver> findPixReceiver() {
        return pixReceivers.find();
    }

    /** @see PixReceiver#of for the refusals */
    @Transactional
    public PixReceiver configurePixReceiver(String key, String receiverName, String city) {
        PixReceiver receiver = PixReceiver.of(key, receiverName, city);
        pixReceivers.save(receiver);
        return receiver;
    }

    // ------------------------------------------------------------------ internals

    /** Expires a request already locked, on its tab already locked. */
    private void expireLocked(TablePaymentRequest request, Tab tab, Instant now) {
        request.expire(tab, now);
        saveAndPublish(request, tab, now);
    }

    private TablePaymentRequest saveAndPublish(TablePaymentRequest request, Tab tab, Instant now) {
        tabs.save(tab);
        TablePaymentRequest saved = requests.save(request);
        events.publishEvent(TablePaymentRequestStatusChanged.of(saved, now));
        return saved;
    }

    /** The clock truncated to microseconds, the precision of {@code timestamptz}. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private DiningTable loadTable(UUID orderingToken) {
        return diningTables.findActiveByOrderingToken(orderingToken)
                .orElseThrow(() -> new DiningTableNotFoundException("No dining table answers this ordering code"));
    }

    private TabId activeTabIdOf(DiningTable diningTable) {
        return tabs.findActiveIdOnDiningTable(diningTable.id()).orElseThrow(() -> noActiveTab());
    }

    private Tab loadTabForUpdate(TabId tabId) {
        return tabs.findByIdForStatusChange(tabId)
                .orElseThrow(() -> new TabNotFoundException("No tab " + tabId.value()));
    }

    private TablePaymentRequest loadForAnswer(TablePaymentRequestId requestId) {
        return requests.findByIdForUpdate(requestId).orElseThrow(() -> notFound(requestId));
    }

    /** No tab id in the detail: it reaches the phone, which is not authenticated. */
    private static TabNotFoundException noActiveTab() {
        return new TabNotFoundException("This dining table has no open tab");
    }

    private static TablePaymentRequestNotFoundException notFound(TablePaymentRequestId requestId) {
        return new TablePaymentRequestNotFoundException("No table payment request " + requestId.value());
    }
}
