package br.com.castel.restaurant.web;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Body of {@code POST /api/restaurant/tabs/{tabId}/transfer}: the destination tab and the lines that
 * go to it.
 *
 * <p>Only {@code toTabId} is checked here, because without it there is nowhere to move to: absent, it
 * answers 400. An absent or empty list of items is a rule of the domain and answers
 * {@code INVALID_TAB_TRANSFER}, so the front gets the same code whether the list came empty or came
 * with nothing in it.
 */
public class TabTransferRequest {

    @NotBlank
    private String toTabId;

    private List<String> itemIds;

    public String getToTabId() {
        return toTabId;
    }

    public void setToTabId(String toTabId) {
        this.toTabId = toTabId;
    }

    public List<String> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<String> itemIds) {
        this.itemIds = itemIds;
    }
}
