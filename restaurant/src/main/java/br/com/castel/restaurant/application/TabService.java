package br.com.castel.restaurant.application;

import br.com.castel.restaurant.domain.DiningTable;
import br.com.castel.restaurant.domain.DiningTableId;
import br.com.castel.restaurant.domain.DiningTableNotFoundException;
import br.com.castel.restaurant.domain.DiningTableRepository;
import br.com.castel.restaurant.domain.MenuItem;
import br.com.castel.restaurant.domain.MenuItemNotFoundException;
import br.com.castel.restaurant.domain.MenuItemRepository;
import br.com.castel.restaurant.domain.ModifierChoice;
import br.com.castel.restaurant.domain.ModifierNotFoundException;
import br.com.castel.restaurant.domain.ModifierRepository;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.restaurant.domain.TabItemOrder;
import br.com.castel.restaurant.domain.TabNotFoundException;
import br.com.castel.restaurant.domain.TabOrigin;
import br.com.castel.restaurant.domain.TabRepository;
import br.com.castel.sharedkernel.AuditorAware;
import br.com.castel.sharedkernel.CurrentProperty;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opening tabs, ordering and cancelling on them, and reading them.
 *
 * <p>Only orchestration: every rule lives in {@link Tab}. The author is the authenticated user, the
 * moment comes from the {@link Clock} and the availability windows are read in the zone of the
 * property.
 *
 * <p>Ordering and cancelling an item load the tab {@code FOR KEY SHARE} (decision #15): two waiters
 * on the same tab do not wait for each other, and the closing of task 3.2 will wait for both.
 * Cancelling the whole tab changes its status, so it loads it {@code FOR UPDATE} and waits for every
 * ordering in progress (decision #18).
 */
@Service
public class TabService {

    private final TabRepository tabs;
    private final DiningTableRepository diningTables;
    private final MenuItemRepository menuItems;
    private final ModifierRepository modifiers;
    private final CurrentProperty currentProperty;
    private final AuditorAware auditorAware;
    private final Clock clock;

    public TabService(
            TabRepository tabs,
            DiningTableRepository diningTables,
            MenuItemRepository menuItems,
            ModifierRepository modifiers,
            CurrentProperty currentProperty,
            AuditorAware auditorAware,
            Clock clock) {
        this.tabs = tabs;
        this.diningTables = diningTables;
        this.menuItems = menuItems;
        this.modifiers = modifiers;
        this.currentProperty = currentProperty;
        this.auditorAware = auditorAware;
        this.clock = clock;
    }

    /**
     * Opens a tab on a dining table or on a self-service card, as the origin says.
     *
     * @throws br.com.castel.restaurant.domain.InvalidTabOpeningException if the origin came without
     *     its field, or with the field of the other origin
     * @throws DiningTableNotFoundException if the table does not exist
     * @throws br.com.castel.restaurant.domain.TabAlreadyOpenForDiningTableException if the table has
     *     an active tab
     * @throws br.com.castel.restaurant.domain.TabAlreadyOpenForCardException if the card has an
     *     active tab
     */
    @Transactional
    public Tab open(TabOrigin origin, DiningTableId diningTableId, Integer cardNumber) {
        origin.requireOpeningFields(diningTableId, cardNumber);
        Tab tab = switch (origin) {
            case TABLE_SERVICE -> Tab.openForTable(
                    currentProperty.id(), loadDiningTable(diningTableId), auditorAware.currentAuditorId(), clock.instant());
            case SELF_SERVICE -> Tab.openForSelfService(
                    currentProperty.id(), cardNumber, auditorAware.currentAuditorId(), clock.instant());
        };
        return tabs.add(tab);
    }

    /**
     * @throws TabNotFoundException if the tab does not exist
     * @throws MenuItemNotFoundException if the menu item does not exist
     * @throws ModifierNotFoundException if a modifier does not exist
     */
    @Transactional
    public Tab addItem(TabId tabId, AddTabItemCommand command) {
        Tab tab = loadForItemEntry(tabId);
        MenuItem menuItem = menuItems.findById(command.menuItemId())
                .orElseThrow(() -> new MenuItemNotFoundException("No menu item " + command.menuItemId().value()));
        List<ModifierChoice> choices = command.modifiers().stream()
                .map(chosen -> new ModifierChoice(
                        modifiers.findById(chosen.modifierId())
                                .orElseThrow(() -> new ModifierNotFoundException(
                                        "No modifier " + chosen.modifierId().value())),
                        chosen.quantity()))
                .toList();
        TabItemOrder order = new TabItemOrder(
                command.variantId(), command.quantity(), command.weightGrams(), choices, command.specialInstructions());
        tab.addItem(menuItem, order, auditorAware.currentAuditorId(), clock.instant(), currentProperty.timeZone());
        return tabs.save(tab);
    }

    /** @throws TabNotFoundException if the tab does not exist */
    @Transactional
    public Tab cancelItem(TabId tabId, TabItemId itemId, String reason) {
        Tab tab = loadForItemEntry(tabId);
        tab.cancelItem(itemId, reason, auditorAware.currentAuditorId(), clock.instant());
        return tabs.save(tab);
    }

    /** @throws TabNotFoundException if the tab does not exist */
    @Transactional
    public Tab cancel(TabId tabId, String reason) {
        Tab tab = tabs.findByIdForStatusChange(tabId).orElseThrow(() -> notFound(tabId));
        tab.cancel(reason, auditorAware.currentAuditorId(), clock.instant());
        return tabs.save(tab);
    }

    /** @throws TabNotFoundException if the tab does not exist */
    @Transactional(readOnly = true)
    public Tab find(TabId tabId) {
        return tabs.findById(tabId).orElseThrow(() -> notFound(tabId));
    }

    /** The {@code OPEN} and {@code CLOSING} tabs, by the moment they opened, narrowed by each filter given. */
    @Transactional(readOnly = true)
    public List<Tab> listActive(DiningTableId diningTableId, Integer cardNumber) {
        return tabs.findActive(currentProperty.id(), diningTableId, cardNumber);
    }

    private Tab loadForItemEntry(TabId tabId) {
        return tabs.findByIdForItemEntry(tabId).orElseThrow(() -> notFound(tabId));
    }

    private DiningTable loadDiningTable(DiningTableId diningTableId) {
        return diningTables.findById(diningTableId)
                .orElseThrow(() -> new DiningTableNotFoundException("No dining table " + diningTableId.value()));
    }

    private static TabNotFoundException notFound(TabId tabId) {
        return new TabNotFoundException("No tab " + tabId.value());
    }
}
