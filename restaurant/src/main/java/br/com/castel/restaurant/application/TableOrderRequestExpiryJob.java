package br.com.castel.restaurant.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires the requests nobody answered in 30 minutes (decisions #14 and #22), and through
 * {@link TableOrderRequestService#expireOverdue} tells the floor about each one.
 *
 * <p>The period is {@code castel.restaurant.table-order-request-expiry-period}, one minute unless
 * configured. A request is shown as expired from the end of its window on, whatever the period:
 * the job only makes it final and pushes the message (decision #29).
 *
 * <p>Switched off with {@code castel.restaurant.table-order-request-expiry-enabled=false}, which the
 * {@code test} profile does so no run of the job lands in the middle of an integration test
 * (decision #41). On unless configured.
 */
@Component
@ConditionalOnProperty(
        name = "castel.restaurant.table-order-request-expiry-enabled", havingValue = "true", matchIfMissing = true)
public class TableOrderRequestExpiryJob {

    private final TableOrderRequestService requests;

    public TableOrderRequestExpiryJob(TableOrderRequestService requests) {
        this.requests = requests;
    }

    @Scheduled(
            fixedDelayString = "${castel.restaurant.table-order-request-expiry-period:PT1M}",
            initialDelayString = "${castel.restaurant.table-order-request-expiry-period:PT1M}")
    public void expireOverdue() {
        requests.expireOverdue();
    }
}
