package br.com.castel.restaurant.web;

/**
 * Body of {@code POST /api/restaurant/table-payment-requests/{id}/refund}: why the waiter undoes the
 * payment. Billing requires it on the refund and on the reversal, and answers its own code when it is
 * missing (decision #30 of task qr-table-payment).
 */
public class TablePaymentRefundRequest {

    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
