package br.com.castel.restaurant.web;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/restaurant/tabs/{tabId}/move}: the dining table the tab moves to. */
public class TabMoveRequest {

    @NotBlank
    private String diningTableId;

    public String getDiningTableId() {
        return diningTableId;
    }

    public void setDiningTableId(String diningTableId) {
        this.diningTableId = diningTableId;
    }
}
