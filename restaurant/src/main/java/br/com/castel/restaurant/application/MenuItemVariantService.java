package br.com.castel.restaurant.application;

import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.restaurant.domain.MenuItem;
import br.com.castel.restaurant.domain.MenuItemNotFoundException;
import br.com.castel.restaurant.domain.MenuItemRepository;
import br.com.castel.restaurant.domain.MenuItemVariantId;
import br.com.castel.sharedkernel.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintaining the variants of an item. Every rule — price, name unique within the item, no variant
 * on an item sold by weight — lives in {@link MenuItem}; this class loads, calls and saves.
 */
@Service
public class MenuItemVariantService {

    private final MenuItemRepository items;

    public MenuItemVariantService(MenuItemRepository items) {
        this.items = items;
    }

    @Transactional
    public MenuItem addVariant(MenuItemId itemId, String name, Money unitPrice) {
        MenuItem item = load(itemId);
        item.addVariant(name, unitPrice);
        return items.save(item);
    }

    /**
     * Changes what the request carries: a null name or price stays as it is. Both changes happen in
     * one transaction, so a refused price does not leave a renamed variant behind.
     */
    @Transactional
    public MenuItem changeVariant(MenuItemId itemId, MenuItemVariantId variantId, String newName, Money newPrice) {
        MenuItem item = load(itemId);
        if (newName != null) {
            item.renameVariant(variantId, newName);
        }
        if (newPrice != null) {
            item.changeVariantPriceTo(variantId, newPrice);
        }
        return items.save(item);
    }

    /** This variant ran out; the others keep being served. */
    @Transactional
    public MenuItem markVariantUnavailable(MenuItemId itemId, MenuItemVariantId variantId) {
        MenuItem item = load(itemId);
        item.markVariantUnavailable(variantId);
        return items.save(item);
    }

    @Transactional
    public MenuItem markVariantAvailable(MenuItemId itemId, MenuItemVariantId variantId) {
        MenuItem item = load(itemId);
        item.markVariantAvailable(variantId);
        return items.save(item);
    }

    @Transactional
    public MenuItem deactivateVariant(MenuItemId itemId, MenuItemVariantId variantId) {
        MenuItem item = load(itemId);
        item.deactivateVariant(variantId);
        return items.save(item);
    }

    @Transactional
    public MenuItem activateVariant(MenuItemId itemId, MenuItemVariantId variantId) {
        MenuItem item = load(itemId);
        item.activateVariant(variantId);
        return items.save(item);
    }

    private MenuItem load(MenuItemId id) {
        return items.findById(id).orElseThrow(() -> new MenuItemNotFoundException("No menu item " + id.value()));
    }
}
