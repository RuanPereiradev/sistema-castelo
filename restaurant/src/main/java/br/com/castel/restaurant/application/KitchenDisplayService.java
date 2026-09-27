package br.com.castel.restaurant.application;

import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.restaurant.domain.TabItemNotFoundException;
import br.com.castel.restaurant.domain.TabItemStatusChanged;
import br.com.castel.restaurant.domain.TabNotFoundException;
import br.com.castel.restaurant.domain.TabRepository;
import br.com.castel.sharedkernel.CurrentProperty;
import br.com.castel.sharedkernel.Settings;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The kitchen display: the queue of each station and the transitions of an item (task 3.5).
 *
 * <p>Only orchestration: every rule lives in {@link Tab}, which returns the event of each transition;
 * this service saves and publishes it, and the screens are told after the commit.
 *
 * <p>Every transition locks the tab {@code FOR KEY SHARE} and then the item {@code FOR UPDATE},
 * like the cancellation of an item: the kitchen marking an item ready and a waiter cancelling it
 * run one after the other, and the second finds the status the first left.
 */
@Service
public class KitchenDisplayService {

    private static final String DELAY_LIMIT_KEY_PREFIX = "restaurant.kitchen-display.";
    private static final String WARNING_KEY_SUFFIX = ".warning-minutes";
    private static final String LATE_KEY_SUFFIX = ".late-minutes";

    private final TabRepository tabs;
    private final KitchenQueue kitchenQueue;
    private final Settings settings;
    private final CurrentProperty currentProperty;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public KitchenDisplayService(
            TabRepository tabs,
            KitchenQueue kitchenQueue,
            Settings settings,
            CurrentProperty currentProperty,
            ApplicationEventPublisher events,
            Clock clock) {
        this.tabs = tabs;
        this.kitchenQueue = kitchenQueue;
        this.settings = settings;
        this.currentProperty = currentProperty;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @throws br.com.castel.sharedkernel.SettingNotFoundException if a delay limit of the station is
     *     not configured
     */
    @Transactional(readOnly = true)
    public KitchenQueueView queue(PrepStation station) {
        return new KitchenQueueView(
                station,
                clock.instant(),
                settings.asInteger(delayLimitKey(station, WARNING_KEY_SUFFIX)),
                settings.asInteger(delayLimitKey(station, LATE_KEY_SUFFIX)),
                kitchenQueue.ticketsOf(currentProperty.id(), station));
    }

    /** The ticket of one item, whatever its status. */
    @Transactional(readOnly = true)
    public Optional<KitchenTicket> ticket(TabItemId itemId) {
        return kitchenQueue.ticket(itemId);
    }

    /** @throws TabItemNotFoundException if no tab holds the item */
    @Transactional
    public KitchenTicket startPreparation(TabItemId itemId) {
        return changeByKitchen(itemId, (tab, at) -> tab.startItemPreparation(itemId, at));
    }

    /** @throws TabItemNotFoundException if no tab holds the item */
    @Transactional
    public KitchenTicket markReady(TabItemId itemId) {
        return changeByKitchen(itemId, (tab, at) -> tab.markItemReady(itemId, at));
    }

    /** @throws TabItemNotFoundException if no tab holds the item */
    @Transactional
    public KitchenTicket undo(TabItemId itemId) {
        return changeByKitchen(itemId, (tab, at) -> tab.undoItemStatus(itemId, at));
    }

    /** Delivered by the waiter (K1, K4). @throws TabNotFoundException if the tab does not exist */
    @Transactional
    public Tab deliver(TabId tabId, TabItemId itemId) {
        Tab tab = tabs.findByIdForItemChange(tabId, itemId)
                .orElseThrow(() -> new TabNotFoundException("No tab " + tabId.value()));
        TabItemStatusChanged change = tab.deliverItem(itemId, clock.instant());
        Tab saved = tabs.save(tab);
        events.publishEvent(change);
        return saved;
    }

    private KitchenTicket changeByKitchen(TabItemId itemId, BiFunction<Tab, Instant, TabItemStatusChanged> transition) {
        Tab tab = tabs.findByItemIdForItemChange(itemId).orElseThrow(() -> itemNotFound(itemId));
        TabItemStatusChanged change = transition.apply(tab, clock.instant());
        tabs.save(tab);
        events.publishEvent(change);
        return kitchenQueue.ticket(itemId).orElseThrow(() -> itemNotFound(itemId));
    }

    /** {@code restaurant.kitchen-display.pizza.warning-minutes}, and the like (decision #20). */
    private static String delayLimitKey(PrepStation station, String suffix) {
        return DELAY_LIMIT_KEY_PREFIX + station.name().toLowerCase(Locale.ROOT) + suffix;
    }

    private static TabItemNotFoundException itemNotFound(TabItemId itemId) {
        return new TabItemNotFoundException("No tab item " + itemId.value());
    }
}
