package br.com.castel.restaurant.web;

import br.com.castel.restaurant.domain.TablePaymentRequestStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tells the floor that a payment from the phone of a table changed, over STOMP, once the change is
 * committed (task qr-table-payment), the same way {@link TableOrderRequestBroadcaster} does for the
 * orders.
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
 *   <li>{@code /topic/restaurant/table-payment-requests}: every payment sent, confirmed, rejected,
 *       expired or refunded, for {@code WAITER} and {@code ADMIN}
 * </ul>
 */
@Component
public class TablePaymentRequestBroadcaster {

    static final String TABLE_PAYMENT_REQUESTS_TOPIC = "/topic/restaurant/table-payment-requests";

    private static final Logger LOGGER = LoggerFactory.getLogger(TablePaymentRequestBroadcaster.class);

    private final SimpMessageSendingOperations messaging;

    public TablePaymentRequestBroadcaster(SimpMessageSendingOperations messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onStatusChanged(TablePaymentRequestStatusChanged event) {
        TablePaymentRequestMessage message = TablePaymentRequestMessage.of(event);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    messaging.convertAndSend(TABLE_PAYMENT_REQUESTS_TOPIC, message);
                } catch (RuntimeException failure) {
                    LOGGER.warn("Could not tell the floor about table payment request {}", event.requestId().value(),
                            failure);
                }
            }
        });
    }
}
