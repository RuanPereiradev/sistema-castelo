package br.com.castel.restaurant.application;

import br.com.castel.restaurant.api.MenuItemId;
import br.com.castel.restaurant.domain.MenuItem;
import br.com.castel.restaurant.domain.MenuItemNotFoundException;
import br.com.castel.restaurant.domain.MenuItemRepository;
import br.com.castel.restaurant.domain.Modifier;
import br.com.castel.restaurant.domain.ModifierId;
import br.com.castel.restaurant.domain.ModifierNotFoundException;
import br.com.castel.restaurant.domain.ModifierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Offering modifiers of the catalog on an item, and withdrawing them. The rules — no modifier on an
 * item sold by weight, no inactive modifier, the quantity range — live in {@link MenuItem}.
 */
@Service
public class MenuItemModifierService {

    private final MenuItemRepository items;
    private final ModifierRepository modifiers;

    public MenuItemModifierService(MenuItemRepository items, ModifierRepository modifiers) {
        this.items = items;
        this.modifiers = modifiers;
    }

    /**
     * @throws MenuItemNotFoundException if the item does not exist
     * @throws ModifierNotFoundException if the modifier does not exist
     */
    @Transactional
    public MenuItem offerModifier(MenuItemId itemId, ModifierId modifierId, int maxQuantity) {
        MenuItem item = loadItem(itemId);
        Modifier modifier = modifiers.findById(modifierId)
                .orElseThrow(() -> new ModifierNotFoundException("No modifier " + modifierId.value()));
        item.offerModifier(modifier, maxQuantity);
        return items.save(item);
    }

    /**
     * @throws MenuItemNotFoundException if the item does not exist
     * @throws ModifierNotFoundException if the item does not offer the modifier
     */
    @Transactional
    public MenuItem withdrawModifier(MenuItemId itemId, ModifierId modifierId) {
        MenuItem item = loadItem(itemId);
        item.withdrawModifier(modifierId);
        return items.save(item);
    }

    private MenuItem loadItem(MenuItemId id) {
        return items.findById(id).orElseThrow(() -> new MenuItemNotFoundException("No menu item " + id.value()));
    }
}
