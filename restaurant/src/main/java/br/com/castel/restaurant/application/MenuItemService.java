package br.com.castel.restaurant.application;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.restaurant.domain.DuplicateMenuItemNameException;
import br.com.castel.restaurant.domain.MenuCategoryNotFoundException;
import br.com.castel.restaurant.domain.MenuCategoryRepository;
import br.com.castel.restaurant.domain.MenuItem;
import br.com.castel.restaurant.domain.MenuItemNotFoundException;
import br.com.castel.restaurant.domain.MenuItemRepository;
import br.com.castel.sharedkernel.CurrentProperty;
import br.com.castel.sharedkernel.Money;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating and maintaining what the restaurant sells. */
@Service
public class MenuItemService {

    private final MenuItemRepository items;
    private final MenuCategoryRepository categories;
    private final CurrentProperty currentProperty;

    public MenuItemService(
            MenuItemRepository items, MenuCategoryRepository categories, CurrentProperty currentProperty) {
        this.items = items;
        this.categories = categories;
        this.currentProperty = currentProperty;
    }

    /**
     * @throws MenuCategoryNotFoundException if the category does not exist
     * @throws DuplicateMenuItemNameException if the property already has an item under this name
     */
    @Transactional
    public MenuItem create(CreateMenuItemCommand command) {
        requireExistingCategory(command.categoryId());
        rejectDuplicate(command.name());
        MenuItem item = command.soldByWeight()
                ? MenuItem.soldByWeight(
                        currentProperty.id(),
                        command.categoryId(),
                        command.name(),
                        command.prepStation(),
                        command.price())
                : MenuItem.soldByUnit(
                        currentProperty.id(),
                        command.categoryId(),
                        command.name(),
                        command.prepStation(),
                        command.price());
        item.describeAs(command.description());
        item.chargeServiceCharge(command.serviceChargeEligible());
        return items.save(item);
    }

    @Transactional
    public MenuItem rename(MenuItemId id, String newName) {
        MenuItem item = load(id);
        rejectDuplicate(newName);
        item.rename(newName);
        return items.save(item);
    }

    @Transactional
    public MenuItem changePrice(MenuItemId id, Money newPrice) {
        MenuItem item = load(id);
        item.changePriceTo(newPrice);
        return items.save(item);
    }

    /** The kitchen ran out. */
    @Transactional
    public MenuItem markUnavailable(MenuItemId id) {
        MenuItem item = load(id);
        item.markUnavailable();
        return items.save(item);
    }

    @Transactional
    public MenuItem markAvailable(MenuItemId id) {
        MenuItem item = load(id);
        item.markAvailable();
        return items.save(item);
    }

    @Transactional
    public MenuItem deactivate(MenuItemId id) {
        MenuItem item = load(id);
        item.deactivate();
        return items.save(item);
    }

    /** Adds a stretch of the day in which the item is served. Null day means every day. */
    @Transactional
    public MenuItem serveBetween(MenuItemId id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        MenuItem item = load(id);
        item.serveBetween(dayOfWeek, startTime, endTime);
        return items.save(item);
    }

    @Transactional(readOnly = true)
    public MenuItem findById(MenuItemId id) {
        return load(id);
    }

    /** Everything, including what is out of the menu: the administration screen shows it all. */
    @Transactional(readOnly = true)
    public List<MenuItem> listAll() {
        return items.findAllOrdered(currentProperty.id());
    }

    /** Only what is on the menu, which is what the public page is built from. */
    @Transactional(readOnly = true)
    public List<MenuItem> listActive() {
        return items.findActiveOrdered(currentProperty.id());
    }

    @Transactional(readOnly = true)
    public List<MenuItem> listActiveInCategory(MenuCategoryId categoryId) {
        requireExistingCategory(categoryId);
        return items.findActiveOrderedByCategory(currentProperty.id(), categoryId);
    }

    private MenuItem load(MenuItemId id) {
        return items.findById(id).orElseThrow(() -> new MenuItemNotFoundException("No menu item " + id.value()));
    }

    private void requireExistingCategory(MenuCategoryId categoryId) {
        if (categories.findById(categoryId).isEmpty()) {
            throw new MenuCategoryNotFoundException("No category " + categoryId.value());
        }
    }

    /**
     * Uniqueness is a rule about the set of items, not about one of them, so it cannot live inside
     * the aggregate. The database holds the same rule in {@code uk_menu_item_name}; this check exists
     * to answer a readable code instead of a constraint violation.
     */
    private void rejectDuplicate(String name) {
        if (name != null && items.existsByName(currentProperty.id(), name.trim())) {
            throw new DuplicateMenuItemNameException("An item named '" + name.trim() + "' already exists");
        }
    }
}
