package br.com.castel.restaurant.web;

/**
 * Body of the two cancellations of a tab: of one item, and of the whole tab.
 *
 * <p>No bean validation: a missing, blank or too long reason is a rule of the domain and answers
 * {@code INVALID_CANCELLATION_REASON} with 422.
 */
public class CancellationRequest {

    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
