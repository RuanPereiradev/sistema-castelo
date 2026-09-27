package br.com.castel.restaurant.web;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.restaurant.application.KitchenDisplayService;
import br.com.castel.restaurant.domain.TabItemCancelled;
import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.restaurant.domain.TabItemOrdered;
import br.com.castel.restaurant.domain.TabItemStatusChanged;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells the screens what changed on the kitchen display, over STOMP, once the change is committed.
 *
 * <p>An outbound adapter, the push counterpart of a controller: it listens to the events of the tab
 * {@link TransactionPhase#AFTER_COMMIT after the commit}, so an order refused or rolled back pushes
 * nothing, then reads the ticket again in a new read-only transaction — the one that published is
 * already over — and sends it.
 *
 * <ul>
 *   <li>{@code /topic/kitchen/{station}}: every change of an item on the queue of the station (K8)
 *   <li>{@code /topic/restaurant/ready-items}: an item entering or leaving {@code READY}, and every
 *       cancellation, for the waiters (K10)
 * </ul>
 *
 * <p>Nothing here ever fails the request: the change is committed already, and an exception thrown
 * after the commit would answer 500 for it. A failure is logged and the screen corrects itself on the
 * next reconnection, which reloads the queue (K13).
 */
@Component
public class KitchenDisplayBroadcaster {

    static final String KITCHEN_TOPIC_PREFIX = "/topic/kitchen/";
    static final String READY_ITEMS_TOPIC = "/topic/restaurant/ready-items";

    private static final Logger LOGGER = LoggerFactory.getLogger(KitchenDisplayBroadcaster.class);

    private final KitchenDisplayService kitchenDisplay;
    private final SimpMessageSendingOperations messaging;

    public KitchenDisplayBroadcaster(KitchenDisplayService kitchenDisplay, SimpMessageSendingOperations messaging) {
        this.kitchenDisplay = kitchenDisplay;
        this.messaging = messaging;
    }

    /** An item sold by weight is born delivered and never reaches the queue (decision #9). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onItemOrdered(TabItemOrdered event) {
        if (!event.reachesKitchenQueue()) {
            return;
        }
        broadcast(event.itemId(), ticket -> {
            KitchenDisplayMessage message = KitchenDisplayMessage.ordered(event.occurredAt(), ticket);
            messaging.convertAndSend(kitchenTopicOf(event.station()), message);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onItemStatusChanged(TabItemStatusChanged event) {
        broadcast(event.itemId(), ticket -> {
            KitchenDisplayMessage message = KitchenDisplayMessage.statusChanged(event.occurredAt(), ticket);
            messaging.convertAndSend(kitchenTopicOf(event.station()), message);
            if (event.touchesReady()) {
                messaging.convertAndSend(READY_ITEMS_TOPIC, message);
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onItemCancelled(TabItemCancelled event) {
        broadcast(event.itemId(), ticket -> {
            KitchenDisplayMessage message = KitchenDisplayMessage.cancelled(event.occurredAt(), ticket, event.reason());
            messaging.convertAndSend(kitchenTopicOf(event.station()), message);
            messaging.convertAndSend(READY_ITEMS_TOPIC, message);
        });
    }

    private void broadcast(TabItemId itemId, Consumer<KitchenTicketResponse> send) {
        try {
            kitchenDisplay.ticket(itemId).map(KitchenTicketResponse::from).ifPresent(send);
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not tell the kitchen display about item {}", itemId.value(), failure);
        }
    }

    static String kitchenTopicOf(PrepStation station) {
        return KITCHEN_TOPIC_PREFIX + station.name();
    }
}
