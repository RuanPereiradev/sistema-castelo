package br.com.castel.restaurant.application;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.domain.DuplicateMenuCategoryNameException;
import br.com.castel.restaurant.domain.MenuCategory;
import br.com.castel.restaurant.domain.MenuCategoryNotFoundException;
import br.com.castel.restaurant.domain.MenuCategoryRepository;
import br.com.castel.sharedkernel.CurrentProperty;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating and maintaining the headings of the menu. */
@Service
public class MenuCategoryService {

    private final MenuCategoryRepository categories;
    private final CurrentProperty currentProperty;

    public MenuCategoryService(MenuCategoryRepository categories, CurrentProperty currentProperty) {
        this.categories = categories;
        this.currentProperty = currentProperty;
    }

    /**
     * @throws DuplicateMenuCategoryNameException if the property already has a category under this name
     */
    @Transactional
    public MenuCategory create(String name, int displayOrder) {
        rejectDuplicate(name);
        return categories.save(MenuCategory.create(currentProperty.id(), name, displayOrder));
    }

    @Transactional
    public MenuCategory rename(MenuCategoryId id, String newName) {
        MenuCategory category = load(id);
        rejectDuplicate(newName);
        category.rename(newName);
        return categories.save(category);
    }

    @Transactional
    public MenuCategory deactivate(MenuCategoryId id) {
        MenuCategory category = load(id);
        category.deactivate();
        return categories.save(category);
    }

    @Transactional(readOnly = true)
    public List<MenuCategory> listAll() {
        return categories.findAllOrdered(currentProperty.id());
    }

    private MenuCategory load(MenuCategoryId id) {
        return categories.findById(id).orElseThrow(() -> new MenuCategoryNotFoundException("No category " + id.value()));
    }

    /**
     * Uniqueness is a rule about the set of categories, not about one of them, so it cannot live
     * inside the aggregate. The database holds the same rule in {@code uk_menu_category_name}, which
     * is what makes it true under concurrency; this check exists to answer a readable code instead of
     * a constraint violation.
     */
    private void rejectDuplicate(String name) {
        if (name != null && categories.existsByName(currentProperty.id(), name.trim())) {
            throw new DuplicateMenuCategoryNameException("A category named '" + name.trim() + "' already exists");
        }
    }
}
