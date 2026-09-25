package br.com.castel.billing.web;

/**
 * Body of the reversal of a charge and of the refund of a payment: the reason alone. A blank or too
 * long reason is refused by the domain, with its code.
 */
public class ReasonRequest {

    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
