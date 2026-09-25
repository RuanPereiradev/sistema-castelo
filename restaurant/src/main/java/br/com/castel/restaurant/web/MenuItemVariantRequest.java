package br.com.castel.restaurant.web;

/**
 * Body of {@code POST and PATCH /api/restaurant/menu-items/{menuItemId}/variants}.
 *
 * <p>No bean validation on purpose: a blank name or a missing price is a rule of the domain, and
 * answers its own code with 422 instead of a generic 400. On {@code PATCH}, an absent field stays as
 * it is. {@code price} travels as a decimal string ({@code "45.00"}).
 */
public class MenuItemVariantRequest {

    private String name;

    private String price;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }
}
