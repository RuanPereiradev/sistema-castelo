package br.com.castel.restaurant.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/restaurant/tabs/{tabId}/merge}: the tab to absorb. The tab of the path is
 * the one that stays (decision T14).
 */
public class TabMergeRequest {

    @NotBlank
    private String mergedTabId;

    public String getMergedTabId() {
        return mergedTabId;
    }

    public void setMergedTabId(String mergedTabId) {
        this.mergedTabId = mergedTabId;
    }
}
