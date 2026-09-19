package br.com.castel.restaurant.web;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.restaurant.application.CreateMenuItemCommand;
import br.com.castel.restaurant.application.MenuCategoryService;
import br.com.castel.restaurant.application.MenuItemService;
import br.com.castel.sharedkernel.Money;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Maintaining the menu. Restricted to {@code ADMIN}: the technical plan puts every {@code cadastro}
 * under that role.
 *
 * <p>Reading the menu is not here — it is open to anyone at {@code /public/menu} (decision #1).
 *
 * <p>Every {@code @PathVariable} names its variable explicitly. Spring can read the name from the
 * bytecode when the module is compiled with {@code -parameters}, but a route that breaks only at
 * runtime, and only when a build flag goes missing, is not worth the saved keystrokes.
 */
@RestController
@RequestMapping("/api/restaurant")
@PreAuthorize("hasRole('ADMIN')")
public class MenuAdministrationController {

    private final MenuItemService menuItems;
    private final MenuCategoryService menuCategories;

    public MenuAdministrationController(MenuItemService menuItems, MenuCategoryService menuCategories) {
        this.menuItems = menuItems;
        this.menuCategories = menuCategories;
    }

    // ------------------------------------------------------------------ categories

    @PostMapping("/menu-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuCategoryResponse createCategory(@Valid @RequestBody CreateMenuCategoryRequest request) {
        return MenuCategoryResponse.from(menuCategories.create(request.getName(), request.getDisplayOrder()));
    }

    @GetMapping("/menu-categories")
    public List<MenuCategoryResponse> listCategories() {
        return menuCategories.listAll().stream().map(MenuCategoryResponse::from).toList();
    }

    // ------------------------------------------------------------------ items

    @PostMapping("/menu-items")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemResponse createItem(@Valid @RequestBody CreateMenuItemRequest request) {
        CreateMenuItemCommand command = new CreateMenuItemCommand(
                MenuCategoryId.of(request.getCategoryId()),
                request.getName(),
                request.getDescription(),
                PrepStation.valueOf(request.getPrepStation()),
                request.isSoldByWeight(),
                Money.of(request.getPrice()),
                request.isServiceChargeEligible());
        return MenuItemResponse.from(menuItems.create(command));
    }

    @GetMapping("/menu-items")
    public List<MenuItemResponse> listItems() {
        return menuItems.listAll().stream().map(MenuItemResponse::from).toList();
    }

    @GetMapping("/menu-items/{menuItemId}")
    public MenuItemResponse findItem(@PathVariable("menuItemId") String menuItemId) {
        return MenuItemResponse.from(menuItems.findById(MenuItemId.of(menuItemId)));
    }

    @PatchMapping("/menu-items/{menuItemId}/price")
    public MenuItemResponse changePrice(@PathVariable("menuItemId") String menuItemId, @Valid @RequestBody ChangePriceRequest request) {
        return MenuItemResponse.from(menuItems.changePrice(MenuItemId.of(menuItemId), Money.of(request.getPrice())));
    }

    /** The kitchen ran out. Overrides the schedule until someone puts the item back. */
    @PostMapping("/menu-items/{menuItemId}/unavailable")
    public MenuItemResponse markUnavailable(@PathVariable("menuItemId") String menuItemId) {
        return MenuItemResponse.from(menuItems.markUnavailable(MenuItemId.of(menuItemId)));
    }

    @PostMapping("/menu-items/{menuItemId}/available")
    public MenuItemResponse markAvailable(@PathVariable("menuItemId") String menuItemId) {
        return MenuItemResponse.from(menuItems.markAvailable(MenuItemId.of(menuItemId)));
    }

    @PostMapping("/menu-items/{menuItemId}/availability-windows")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemResponse addAvailabilityWindow(
            @PathVariable("menuItemId") String menuItemId, @Valid @RequestBody AvailabilityWindowRequest request) {
        DayOfWeek dayOfWeek = request.getDayOfWeek() == null ? null : DayOfWeek.valueOf(request.getDayOfWeek());
        return MenuItemResponse.from(menuItems.serveBetween(
                MenuItemId.of(menuItemId),
                dayOfWeek,
                LocalTime.parse(request.getStartTime()),
                LocalTime.parse(request.getEndTime())));
    }
}
