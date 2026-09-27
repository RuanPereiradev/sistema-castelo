package br.com.castel.restaurant.web;

/**
 * Body of {@code POST /api/restaurant/tabs/{tabId}/reopen}. No bean validation: a missing, blank or
 * too long reason is a rule of the domain and answers {@code INVALID_REOPENING_REASON}.
 */
public class ReopeningRequest {

    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
