package br.com.castel.restaurant.web;

import java.util.List;

/** A heading of the public menu, with the items under it. */
public record PublicMenuCategoryResponse(String name, List<PublicMenuItemResponse> items) {
}
