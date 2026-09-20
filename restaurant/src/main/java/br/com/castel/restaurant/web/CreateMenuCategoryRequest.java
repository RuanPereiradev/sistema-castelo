package br.com.castel.restaurant.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/restaurant/menu-categories}. */
public class CreateMenuCategoryRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    private int displayOrder;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
