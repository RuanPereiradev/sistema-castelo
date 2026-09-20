package br.com.castel.restaurant.web;

import java.util.List;

/** The whole public menu: the categories on the menu, each with its items. */
public record PublicMenuResponse(List<PublicMenuCategoryResponse> categories) {
}
