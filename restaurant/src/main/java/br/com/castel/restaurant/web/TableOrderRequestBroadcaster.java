package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.TableOrderRequestStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tells the floor that a request from the QR code of a table changed, over STOMP, once the change is
 * committed (decisions #10 and #25).
 *
 * <p>The same shape as {@link KitchenDisplayBroadcaster}: the message is built
 * {@link TransactionPhase#BEFORE_COMMIT before the commit} and handed to an {@code afterCommit}
 * callback that only sends, so a request refused or rolled back pushes nothing, and the messages go
 * out in commit order. The message carries only ids, so nothing is read from the database.
 *
 * <p>A failure sending is caught and logged: by then the change is committed, and an exception from
 * {@code afterCommit} would answer 500 for it. The floor corrects itself on its next reload.
 *
 * <ul>
 *   <li>{@code /topic/restaurant/table-order-requests}: every request sent, confirmed, rejected or
 *       expired, for {@code WAITER} and {@code ADMIN}
 * </ul>
 */
@Component
public class TableOrderRequestBroadcaster {

    static final String TABLE_ORDER_REQUESTS_TOPIC = "/topic/restaurant/table-order-requests";

    private static final Logger LOGGER = LoggerFactory.getLogger(TableOrderRequestBroadcaster.class);

    private final SimpMessageSendingOperations messaging;

    public TableOrderRequestBroadcaster(SimpMessageSendingOperations messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onStatusChanged(TableOrderRequestStatusChanged event) {
        TableOrderRequestMessage message = TableOrderRequestMessage.of(event);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    messaging.convertAndSend(TABLE_ORDER_REQUESTS_TOPIC, message);
                } catch (RuntimeException failure) {
                    LOGGER.warn("Could not tell the floor about table order request {}", event.requestId().value(),
                            failure);
                }
            }
        });
    }
}
