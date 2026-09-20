package br.com.castel.restaurant.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/restaurant/menu-items}.
 *
 * <p>{@code price} travels as a decimal string ({@code "62.00"}), like every monetary value of the
 * API, so JavaScript never rounds it. It is read as a unit price, or as a price per kilo when
 * {@code soldByWeight} is true.
 */
public class CreateMenuItemRequest {

    @NotBlank
    private String categoryId;

    @NotBlank
    @Size(max = 150)
    private String name;

    private String description;

    @NotBlank
    private String prepStation;

    private boolean soldByWeight;

    @NotNull
    private String price;

    private boolean serviceChargeEligible = true;

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPrepStation() {
        return prepStation;
    }

    public void setPrepStation(String prepStation) {
        this.prepStation = prepStation;
    }

    public boolean isSoldByWeight() {
        return soldByWeight;
    }

    public void setSoldByWeight(boolean soldByWeight) {
        this.soldByWeight = soldByWeight;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public boolean isServiceChargeEligible() {
        return serviceChargeEligible;
    }

    public void setServiceChargeEligible(boolean serviceChargeEligible) {
        this.serviceChargeEligible = serviceChargeEligible;
    }
}
