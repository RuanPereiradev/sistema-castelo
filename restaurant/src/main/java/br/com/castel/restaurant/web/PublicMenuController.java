package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.MenuCategoryService;
import br.com.castel.restaurant.application.MenuItemService;
import br.com.castel.restaurant.application.ModifierService;
import br.com.castel.restaurant.domain.MenuCategory;
import br.com.castel.restaurant.domain.MenuItem;
import br.com.castel.restaurant.domain.Modifier;
import br.com.castel.restaurant.domain.ModifierId;
import br.com.castel.sharedkernel.CurrentProperty;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The menu, open to anyone with the link (decision #1).
 *
 * <p>Lives under {@code /public} and not under {@code /api} on purpose: the technical plan reserves
 * that prefix for what is open, and a route without authentication sitting among authenticated ones
 * is how a route ends up unprotected by accident (decision #2).
 *
 * <p>What it answers is narrower than the administration view: no preparation station, no active
 * flag, no audit columns (decision #3).
 */
@RestController
@RequestMapping("/public")
public class PublicMenuController {

    private final MenuItemService menuItems;
    private final MenuCategoryService menuCategories;
    private final ModifierService modifiers;
    private final CurrentProperty currentProperty;
    private final Clock clock;

    public PublicMenuController(
            MenuItemService menuItems,
            MenuCategoryService menuCategories,
            ModifierService modifiers,
            CurrentProperty currentProperty,
            Clock clock) {
        this.menuItems = menuItems;
        this.menuCategories = menuCategories;
        this.modifiers = modifiers;
        this.currentProperty = currentProperty;
        this.clock = clock;
    }

    @GetMapping("/menu")
    public PublicMenuResponse readMenu() {
        Instant now = clock.instant();
        ZoneId zone = currentProperty.timeZone();
        Map<ModifierId, Modifier> activeModifiersById = modifiers.listAll().stream()
                .filter(Modifier::isActive)
                .collect(Collectors.toMap(Modifier::id, Function.identity()));

        Map<String, List<MenuItem>> itemsByCategory = menuItems.listActive().stream()
                .collect(Collectors.groupingBy(item -> item.menuCategoryId().value().toString()));

        List<PublicMenuCategoryResponse> categories = menuCategories.listAll().stream()
                .filter(MenuCategory::isActive)
                .map(category -> new PublicMenuCategoryResponse(
                        category.name(),
                        itemsByCategory.getOrDefault(category.id().value().toString(), List.of()).stream()
                                .map(item -> PublicMenuItemResponse.from(item, activeModifiersById, now, zone))
                                .toList()))
                .filter(category -> !category.items().isEmpty())
                .toList();

        return new PublicMenuResponse(categories);
    }
}
