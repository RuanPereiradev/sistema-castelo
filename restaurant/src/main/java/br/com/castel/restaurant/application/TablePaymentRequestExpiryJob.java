package br.com.castel.restaurant.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires the payments from the phone nobody confirmed in 30 minutes, freeing their items, and through
 * {@link TablePaymentRequestService#expireOverdue} tells the floor about each one (decisions #8 and
 * #34 of task qr-table-payment).
 *
 * <p>The period is {@code castel.restaurant.table-payment-request-expiry-period}, one minute unless
 * configured. A request is shown as expired from the end of its window on, whatever the period: the
 * job only makes it final, frees the items and pushes the message.
 *
 * <p>Switched off with {@code castel.restaurant.table-payment-request-expiry-enabled=false}, which the
 * {@code test} profile does so no run of the job lands in the middle of an integration test. On
 * unless configured.
 */
@Component
@ConditionalOnProperty(
        name = "castel.restaurant.table-payment-request-expiry-enabled", havingValue = "true", matchIfMissing = true)
public class TablePaymentRequestExpiryJob {

    private final TablePaymentRequestService requests;

    public TablePaymentRequestExpiryJob(TablePaymentRequestService requests) {
        this.requests = requests;
    }

    @Scheduled(
            fixedDelayString = "${castel.restaurant.table-payment-request-expiry-period:PT1M}",
            initialDelayString = "${castel.restaurant.table-payment-request-expiry-period:PT1M}")
    public void expireOverdue() {
        requests.expireOverdue();
    }
}
