package br.com.castel.restaurant.application;

import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabItem;
import br.com.castel.sharedkernel.Percentage;
import java.util.Objects;
import java.util.Optional;

/**
 * The active tab of a table as its phone reads it (decision #1 of task qr-table-payment).
 *
 * @param currentRate the rate of the setting now; the tab uses its frozen one while {@code CLOSING}
 * @param pixConfigured whether a Pix receiver is configured; without one the phone does not offer to
 *     pay (decision #15)
 * @param customerDeviceId the device reading, to tell its own items from the rest (decision #40);
 *     empty when the phone did not say
 */
public record TablePaymentTabView(DiningTable diningTable, Tab tab, Percentage currentRate, boolean pixConfigured,
        Optional<String> customerDeviceId) {

    public TablePaymentTabView {
        Objects.requireNonNull(diningTable, "diningTable");
        Objects.requireNonNull(tab, "tab");
        Objects.requireNonNull(currentRate, "currentRate");
        Objects.requireNonNull(customerDeviceId, "customerDeviceId");
    }

    /** Whether the item was ordered from the device reading ("Só os meus"); never true without a device. */
    public boolean isOfReadingDevice(TabItem item) {
        return customerDeviceId.isPresent() && item.customerDeviceId().equals(customerDeviceId);
    }

    /** Whether the phone offers to pay: a Pix receiver configured and a tab that takes it. */
    public boolean acceptsPayment() {
        return pixConfigured && tab.status().acceptsTablePayment();
    }
}
