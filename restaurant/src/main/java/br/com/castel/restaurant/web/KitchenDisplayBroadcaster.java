package br.com.castel.restaurant.web;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.restaurant.application.KitchenQueue;
import br.com.castel.restaurant.domain.TabItemCancelled;
import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.restaurant.domain.TabItemOrdered;
import br.com.castel.restaurant.domain.TabItemStatusChanged;
import br.com.castel.restaurant.domain.TabItemTransferred;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tells the screens what changed on the kitchen display, over STOMP, once the change is committed.
 *
 * <p>An outbound adapter, the push counterpart of a controller. It reads the ticket
 * {@link TransactionPhase#BEFORE_COMMIT before the commit}, on the connection the transaction already
 * holds, and hands the message to an {@code afterCommit} callback that only sends. Nothing after the
 * commit touches the database: reading there would ask the pool for a second connection while the
 * first has not gone back yet, and enough simultaneous orders would exhaust it (review, round 1). An
 * order refused or rolled back never reaches {@code afterCommit}, so it pushes nothing. The callbacks
 * run in commit order, which keeps the order of the messages.
 *
 * <ul>
 *   <li>{@code /topic/kitchen/{station}}: every change of an item on the queue of the station (K8)
 *   <li>{@code /topic/restaurant/ready-items}: an item entering or leaving {@code READY}, every
 *       cancellation, and an item that moved while {@code READY}, for the waiters (K10)
 * </ul>
 *
 * <p>A failure reading the ticket <b>fails the request</b> (review, round 3): it propagates out of the
 * commit, the transaction is rolled back and the waiter repeats the order. It is never swallowed,
 * because on PostgreSQL an SQL error aborts the transaction and the commit that follows rolls back in
 * silence: swallowing it answered 201 for an order that was never stored. The port flushes the
 * pending writes before it reads, so a violation of the order's own write surfaces with its own
 * cause, not as a failed read.
 *
 * <p>A failure <em>sending</em> is caught and logged: by then the change is committed, and an exception
 * thrown from {@code afterCommit} reaches the caller of the commit and would answer 500 for it. The
 * screen corrects itself on the next reconnection, which reloads the queue (K13).
 */
@Component
public class KitchenDisplayBroadcaster {

    static final String KITCHEN_TOPIC_PREFIX = "/topic/kitchen/";
    static final String READY_ITEMS_TOPIC = "/topic/restaurant/ready-items";

    private static final Logger LOGGER = LoggerFactory.getLogger(KitchenDisplayBroadcaster.class);

    private final KitchenQueue kitchenQueue;
    private final SimpMessageSendingOperations messaging;

    public KitchenDisplayBroadcaster(KitchenQueue kitchenQueue, SimpMessageSendingOperations messaging) {
        this.kitchenQueue = kitchenQueue;
        this.messaging = messaging;
    }

    /** An item sold by weight is born delivered and never reaches the queue (decision #9). */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onItemOrdered(TabItemOrdered event) {
        if (!event.reachesKitchenQueue()) {
            return;
        }
        sendAfterCommit(event.itemId(), ticket -> {
            KitchenDisplayMessage message = KitchenDisplayMessage.ordered(event.occurredAt(), ticket);
            messaging.convertAndSend(kitchenTopicOf(event.station()), message);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onItemStatusChanged(TabItemStatusChanged event) {
        sendAfterCommit(event.itemId(), ticket -> {
            KitchenDisplayMessage message = KitchenDisplayMessage.statusChanged(event.occurredAt(), ticket);
            messaging.convertAndSend(kitchenTopicOf(event.station()), message);
            if (event.touchesReady()) {
                messaging.convertAndSend(READY_ITEMS_TOPIC, message);
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onItemCancelled(TabItemCancelled event) {
        sendAfterCommit(event.itemId(), ticket -> {
            KitchenDisplayMessage message = KitchenDisplayMessage.cancelled(event.occurredAt(), ticket, event.reason());
            messaging.convertAndSend(kitchenTopicOf(event.station()), message);
            messaging.convertAndSend(READY_ITEMS_TOPIC, message);
        });
    }

    /**
     * An item that moved to another tab (task 3.6): the ticket is pushed again, now under the table
     * of the tab it arrived on. An item already delivered — a plate sold by weight among them — is
     * not on any queue and generates nothing.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onItemTransferred(TabItemTransferred event) {
        if (!event.reachesKitchenQueue()) {
            return;
        }
        sendAfterCommit(event.itemId(), ticket -> {
            KitchenDisplayMessage message = KitchenDisplayMessage.transferred(event.occurredAt(), ticket);
            messaging.convertAndSend(kitchenTopicOf(event.station()), message);
            if (event.isReady()) {
                messaging.convertAndSend(READY_ITEMS_TOPIC, message);
            }
        });
    }

    /**
     * Reads the ticket now, inside the transaction, and sends it once the transaction commits. A
     * failure of the read propagates and rolls the change back.
     */
    private void sendAfterCommit(TabItemId itemId, Consumer<KitchenTicketResponse> send) {
        KitchenTicketResponse ticket = kitchenQueue.ticket(itemId).map(KitchenTicketResponse::from).orElse(null);
        if (ticket == null) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    send.accept(ticket);
                } catch (RuntimeException failure) {
                    LOGGER.warn("Could not tell the kitchen display about item {}", itemId.value(), failure);
                }
            }
        });
    }

    static String kitchenTopicOf(PrepStation station) {
        return KITCHEN_TOPIC_PREFIX + station.name();
    }
}
