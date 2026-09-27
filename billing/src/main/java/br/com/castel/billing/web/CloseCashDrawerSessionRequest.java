package br.com.castel.billing.web;

/**
 * Body of {@code POST /api/billing/cash-sessions/{sessionId}/close}: the count of the drawer, as a
 * decimal string, and the note that justifies a difference. The note is optional when there is none.
 */
public class CloseCashDrawerSessionRequest {

    private String countedAmount;

    private String note;

    public String getCountedAmount() {
        return countedAmount;
    }

    public void setCountedAmount(String countedAmount) {
        this.countedAmount = countedAmount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
