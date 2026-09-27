package br.com.castel.restaurant.web;

/**
 * Body of {@code PUT /api/restaurant/tabs/{tabId}/guest-count}. No bean validation: a count outside 1
 * to 999, absent included, is a rule of the domain and answers {@code INVALID_GUEST_COUNT}.
 */
public class GuestCountRequest {

    private Integer guestCount;

    public Integer getGuestCount() {
        return guestCount;
    }

    public void setGuestCount(Integer guestCount) {
        this.guestCount = guestCount;
    }
}
