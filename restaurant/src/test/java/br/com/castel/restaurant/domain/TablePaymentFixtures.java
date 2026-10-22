package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.PROPERTY_ID;
import static br.com.castel.restaurant.domain.TabFixtures.TEN_PERCENT;
import static br.com.castel.restaurant.domain.TabFixtures.wednesdayAt;

import br.com.castel.sharedkernel.Percentage;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Shared arrangement for the tests of the payment from the phone of the table (task qr-table-payment). */
final class TablePaymentFixtures {

    static final String TAB_ITEM_ALREADY_PAID = "TAB_ITEM_ALREADY_PAID";
    static final String TAB_ITEM_RESERVED_FOR_PAYMENT = "TAB_ITEM_RESERVED_FOR_PAYMENT";
    static final String PIX_RECEIVER_NOT_CONFIGURED = "PIX_RECEIVER_NOT_CONFIGURED";
    static final String INVALID_TABLE_PAYMENT_REQUEST = "INVALID_TABLE_PAYMENT_REQUEST";
    static final String NOT_PENDING = "TABLE_PAYMENT_REQUEST_NOT_PENDING";
    static final String EXPIRED = "TABLE_PAYMENT_REQUEST_EXPIRED";
    static final String NOT_REFUNDABLE = "TABLE_PAYMENT_REQUEST_NOT_REFUNDABLE";
    static final String INVALID_PIX_RECEIVER = "INVALID_PIX_RECEIVER";
    static final String INVALID_CUSTOMER_NAME = "INVALID_CUSTOMER_NAME";
    static final String INVALID_CUSTOMER_DEVICE_ID = "INVALID_CUSTOMER_DEVICE_ID";

    static final String CUSTOMER_NAME = "Ana";
    static final String DEVICE_ID = "phone-of-ana-0001";
    static final String OTHER_CUSTOMER_NAME = "Bruno";
    static final String OTHER_DEVICE_ID = "phone-of-bruno-02";

    static final PixReceiver RECEIVER = PixReceiver.of("pix@castel.com.br", "Castel Restaurante", "Fortaleza");

    /** Inside the OPEN life of the tabs of {@link TabFixtures}, which close at 13:00. */
    static final Instant REQUESTED_AT = wednesdayAt("12:20");
    static final Instant WINDOW_END = wednesdayAt("12:50");
    static final Instant CONFIRMED_AT = wednesdayAt("12:30");
    static final Instant AFTER_WINDOW = wednesdayAt("12:51");

    /** After {@link TabFixtures#CLOSING_AT}, for the requests sent while the tab is CLOSING. */
    static final Instant REQUESTED_WHILE_CLOSING_AT = wednesdayAt("13:05");
    static final Instant CONFIRMED_WHILE_CLOSING_AT = wednesdayAt("13:10");
    static final Instant REFUNDED_AT = wednesdayAt("13:15");

    static final String REFUND_REASON = "Comprovante era de outra mesa";

    private TablePaymentFixtures() {
    }

    /** Every unit of the item. */
    static Tab.PartChoice whole(TabItem item) {
        return new Tab.PartChoice(item.id(), item.quantity());
    }

    /** Some units of the item. */
    static Tab.PartChoice choice(TabItem item, Integer quantity) {
        return new Tab.PartChoice(item.id(), quantity);
    }

    /** Every unit of each item, with the service charge, at 10%, by Ana. */
    static TablePaymentRequest request(Tab tab, TabItem... items) {
        return requestAt(tab, TEN_PERCENT, REQUESTED_AT, items);
    }

    static TablePaymentRequest requestAt(Tab tab, Percentage currentRate, Instant at, TabItem... items) {
        return TablePaymentRequest.request(PROPERTY_ID, tab, CUSTOMER_NAME, DEVICE_ID,
                Arrays.stream(items).map(TablePaymentFixtures::whole).toList(), true, currentRate,
                Optional.of(RECEIVER), at);
    }

    /** The units chosen, with or without the service charge, at 10%, by Ana. */
    static TablePaymentRequest requestUnits(Tab tab, boolean includeServiceCharge, Tab.PartChoice... choices) {
        return TablePaymentRequest.request(PROPERTY_ID, tab, CUSTOMER_NAME, DEVICE_ID, List.of(choices),
                includeServiceCharge, TEN_PERCENT, Optional.of(RECEIVER), REQUESTED_AT);
    }

    /** The same request with another customer, to prove the validation of name and device. */
    static TablePaymentRequest requestBy(Tab tab, String customerName, String customerDeviceId, TabItem item) {
        return TablePaymentRequest.request(PROPERTY_ID, tab, customerName, customerDeviceId, List.of(whole(item)),
                true, TEN_PERCENT, Optional.of(RECEIVER), REQUESTED_AT);
    }
}
