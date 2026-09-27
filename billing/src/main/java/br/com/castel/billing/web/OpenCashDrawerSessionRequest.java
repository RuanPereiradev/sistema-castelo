package br.com.castel.billing.web;

/**
 * Body of {@code POST /api/billing/cash-sessions}. The float is a decimal string; a missing or
 * malformed one is refused by {@code Money}, a negative one by the domain, each with its code.
 */
public class OpenCashDrawerSessionRequest {

    private String openingFloat;

    public String getOpeningFloat() {
        return openingFloat;
    }

    public void setOpeningFloat(String openingFloat) {
        this.openingFloat = openingFloat;
    }
}
