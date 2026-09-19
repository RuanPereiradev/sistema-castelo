package br.com.castel.restaurant.web;

import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /api/restaurant/menu-items/{menuItemId}/price}. */
public class ChangePriceRequest {

    @NotNull
    private String price;

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }
}
