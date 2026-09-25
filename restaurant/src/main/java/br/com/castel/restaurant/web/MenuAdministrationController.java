package br.com.castel.restaurant.web;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.restaurant.application.CreateMenuItemCommand;
import br.com.castel.restaurant.application.MenuCategoryService;
import br.com.castel.restaurant.application.MenuItemModifierService;
import br.com.castel.restaurant.application.MenuItemService;
import br.com.castel.restaurant.application.MenuItemVariantService;
import br.com.castel.restaurant.application.ModifierService;
import br.com.castel.restaurant.domain.MenuItem;
import br.com.castel.restaurant.domain.MenuItemVariantId;
import br.com.castel.restaurant.domain.Modifier;
import br.com.castel.restaurant.domain.ModifierId;
import br.com.castel.sharedkernel.Money;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final MenuItemVariantService menuItemVariants;
    private final MenuItemModifierService menuItemModifiers;
    private final ModifierService modifiers;

    public MenuAdministrationController(
            MenuItemService menuItems,
            MenuCategoryService menuCategories,
            MenuItemVariantService menuItemVariants,
            MenuItemModifierService menuItemModifiers,
            ModifierService modifiers) {
        this.menuItems = menuItems;
        this.menuCategories = menuCategories;
        this.menuItemVariants = menuItemVariants;
        this.menuItemModifiers = menuItemModifiers;
        this.modifiers = modifiers;
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
        return respond(menuItems.create(command));
    }

    @GetMapping("/menu-items")
    public List<MenuItemResponse> listItems() {
        Map<ModifierId, Modifier> catalog = modifierCatalog();
        return menuItems.listAll().stream().map(item -> MenuItemResponse.from(item, catalog)).toList();
    }

    @GetMapping("/menu-items/{menuItemId}")
    public MenuItemResponse findItem(@PathVariable("menuItemId") String menuItemId) {
        return respond(menuItems.findById(MenuItemId.of(menuItemId)));
    }

    @PatchMapping("/menu-items/{menuItemId}/price")
    public MenuItemResponse changePrice(@PathVariable("menuItemId") String menuItemId, @Valid @RequestBody ChangePriceRequest request) {
        return respond(menuItems.changePrice(MenuItemId.of(menuItemId), Money.of(request.getPrice())));
    }

    /** The kitchen ran out. Overrides the schedule until someone puts the item back. */
    @PostMapping("/menu-items/{menuItemId}/unavailable")
    public MenuItemResponse markUnavailable(@PathVariable("menuItemId") String menuItemId) {
        return respond(menuItems.markUnavailable(MenuItemId.of(menuItemId)));
    }

    @PostMapping("/menu-items/{menuItemId}/available")
    public MenuItemResponse markAvailable(@PathVariable("menuItemId") String menuItemId) {
        return respond(menuItems.markAvailable(MenuItemId.of(menuItemId)));
    }

    @PostMapping("/menu-items/{menuItemId}/availability-windows")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemResponse addAvailabilityWindow(
            @PathVariable("menuItemId") String menuItemId, @Valid @RequestBody AvailabilityWindowRequest request) {
        DayOfWeek dayOfWeek = request.getDayOfWeek() == null ? null : DayOfWeek.valueOf(request.getDayOfWeek());
        return respond(menuItems.serveBetween(
                MenuItemId.of(menuItemId),
                dayOfWeek,
                LocalTime.parse(request.getStartTime()),
                LocalTime.parse(request.getEndTime())));
    }

    // ------------------------------------------------------------------ variants

    @PostMapping("/menu-items/{menuItemId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemResponse addVariant(
            @PathVariable("menuItemId") String menuItemId, @RequestBody MenuItemVariantRequest request) {
        return respond(menuItemVariants.addVariant(
                MenuItemId.of(menuItemId), request.getName(), moneyOrNull(request.getPrice())));
    }

    /** Changes the name, the price, or both. An absent field stays as it is. */
    @PatchMapping("/menu-items/{menuItemId}/variants/{variantId}")
    public MenuItemResponse changeVariant(
            @PathVariable("menuItemId") String menuItemId,
            @PathVariable("variantId") String variantId,
            @RequestBody MenuItemVariantRequest request) {
        return respond(menuItemVariants.changeVariant(
                MenuItemId.of(menuItemId),
                MenuItemVariantId.of(variantId),
                request.getName(),
                moneyOrNull(request.getPrice())));
    }

    /** This variant ran out; the others keep being served. */
    @PostMapping("/menu-items/{menuItemId}/variants/{variantId}/unavailable")
    public MenuItemResponse markVariantUnavailable(
            @PathVariable("menuItemId") String menuItemId, @PathVariable("variantId") String variantId) {
        return respond(
                menuItemVariants.markVariantUnavailable(MenuItemId.of(menuItemId), MenuItemVariantId.of(variantId)));
    }

    @PostMapping("/menu-items/{menuItemId}/variants/{variantId}/available")
    public MenuItemResponse markVariantAvailable(
            @PathVariable("menuItemId") String menuItemId, @PathVariable("variantId") String variantId) {
        return respond(
                menuItemVariants.markVariantAvailable(MenuItemId.of(menuItemId), MenuItemVariantId.of(variantId)));
    }

    @PostMapping("/menu-items/{menuItemId}/variants/{variantId}/deactivate")
    public MenuItemResponse deactivateVariant(
            @PathVariable("menuItemId") String menuItemId, @PathVariable("variantId") String variantId) {
        return respond(
                menuItemVariants.deactivateVariant(MenuItemId.of(menuItemId), MenuItemVariantId.of(variantId)));
    }

    @PostMapping("/menu-items/{menuItemId}/variants/{variantId}/activate")
    public MenuItemResponse activateVariant(
            @PathVariable("menuItemId") String menuItemId, @PathVariable("variantId") String variantId) {
        return respond(
                menuItemVariants.activateVariant(MenuItemId.of(menuItemId), MenuItemVariantId.of(variantId)));
    }

    // ------------------------------------------------------------------ modifiers offered on an item

    /** Offers a modifier of the catalog on the item. Offering it again replaces the maximum quantity. */
    @PutMapping("/menu-items/{menuItemId}/modifiers/{modifierId}")
    public MenuItemResponse offerModifier(
            @PathVariable("menuItemId") String menuItemId,
            @PathVariable("modifierId") String modifierId,
            @Valid @RequestBody OfferModifierRequest request) {
        return respond(menuItemModifiers.offerModifier(
                MenuItemId.of(menuItemId), ModifierId.of(modifierId), request.getMaxQuantity()));
    }

    @DeleteMapping("/menu-items/{menuItemId}/modifiers/{modifierId}")
    public MenuItemResponse withdrawModifier(
            @PathVariable("menuItemId") String menuItemId, @PathVariable("modifierId") String modifierId) {
        return respond(
                menuItemModifiers.withdrawModifier(MenuItemId.of(menuItemId), ModifierId.of(modifierId)));
    }

    /** The item with the name, price and state of each modifier it offers (decision #26). */
    private MenuItemResponse respond(MenuItem item) {
        return MenuItemResponse.from(item, modifierCatalog());
    }

    /** Every modifier of the property, active or not: a link to an inactive one is still shown. */
    private Map<ModifierId, Modifier> modifierCatalog() {
        return modifiers.listAll().stream().collect(Collectors.toMap(Modifier::id, Function.identity()));
    }

    /** A missing price reaches the aggregate as null, which refuses it with its own code. */
    private static Money moneyOrNull(String amount) {
        return amount == null ? null : Money.of(amount);
    }
}
