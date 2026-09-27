package br.com.castel.restaurant.application;

import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.billing.api.ReceivedPaymentView;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabBilling;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.restaurant.domain.TabItemId;
import br.com.castel.restaurant.domain.TabNotFoundException;
import br.com.castel.restaurant.domain.TabRepository;
import br.com.castel.sharedkernel.AuditorAware;
import br.com.castel.sharedkernel.Money;
import br.com.castel.sharedkernel.Percentage;
import br.com.castel.sharedkernel.Settings;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closing a tab (task 3.2): the service charge, the split of the bill, the pre-bill, payments,
 * reopening and the final close.
 *
 * <p>Only orchestration: every rule lives in {@link Tab}, which talks to billing through the
 * {@link TabBilling} port, in this same transaction. A class of its own, apart from
 * {@link TabService}, so the kitchen display of task 3.5 and this task do not compete for one file.
 *
 * <p>Locks, always the tab before the folio: a change of the tab's status ({@code startClosing},
 * {@code reopen}, {@code close}) loads it {@code FOR UPDATE}, waiting for every
 * ordering, payment and change of the split in progress; those load it {@code FOR KEY SHARE}, so two
 * waiters receiving at once do not wait for each other on the tab and queue on the lock of the folio.
 *
 * <p>The rate of the service charge is read from the setting on every call; the tab uses it only
 * while no rate is frozen.
 */
@Service
public class TabClosingService {

    /** Percent points, {@code 10.00} for ten percent (decision F1). */
    public static final String SERVICE_CHARGE_PERCENT_SETTING = "restaurant.service-charge-percent";

    private final TabRepository tabs;
    private final TabBilling billing;
    private final Settings settings;
    private final AuditorAware auditorAware;
    private final Clock clock;

    public TabClosingService(
            TabRepository tabs, TabBilling billing, Settings settings, AuditorAware auditorAware, Clock clock) {
        this.tabs = tabs;
        this.billing = billing;
        this.settings = settings;
        this.auditorAware = auditorAware;
        this.clock = clock;
    }

    /** @throws TabNotFoundException if the tab does not exist */
    @Transactional
    public TabClosingView removeServiceCharge(TabId tabId) {
        Tab tab = loadShared(tabId);
        tab.removeServiceCharge();
        return view(tabs.save(tab));
    }

    /** @throws TabNotFoundException if the tab does not exist */
    @Transactional
    public TabClosingView restoreServiceCharge(TabId tabId) {
        Tab tab = loadShared(tabId);
        tab.restoreServiceCharge();
        return view(tabs.save(tab));
    }

    /** @throws TabNotFoundException if the tab does not exist */
    @Transactional
    public TabClosingView removeServiceChargeFrom(TabId tabId, TabItemId itemId) {
        Tab tab = loadShared(tabId);
        tab.removeServiceChargeFrom(itemId);
        return view(tabs.save(tab));
    }

    /** @throws TabNotFoundException if the tab does not exist */
    @Transactional
    public TabClosingView restoreServiceChargeTo(TabId tabId, TabItemId itemId) {
        Tab tab = loadShared(tabId);
        tab.restoreServiceChargeTo(itemId);
        return view(tabs.save(tab));
    }

    /** @throws TabNotFoundException if the tab does not exist */
    @Transactional
    public TabClosingView assignSplitGroups(TabId tabId, Map<TabItemId, Integer> assignments) {
        Tab tab = loadShared(tabId);
        tab.assignToSplitGroup(assignments);
        return view(tabs.save(tab));
    }

    /** @throws TabNotFoundException if the tab does not exist */
    @Transactional
    public TabClosingView recordGuestCount(TabId tabId, int guestCount) {
        Tab tab = loadShared(tabId);
        tab.recordGuestCount(guestCount);
        return view(tabs.save(tab));
    }

    /** @throws TabNotFoundException if the tab does not exist */
    @Transactional(readOnly = true)
    public TabClosingView bill(TabId tabId) {
        return view(tabs.findById(tabId).orElseThrow(() -> notFound(tabId)));
    }

    /** @throws TabNotFoundException if the tab does not exist */
    @Transactional
    public TabClosingView startClosing(TabId tabId) {
        Tab tab = loadExclusive(tabId);
        tab.startClosing(currentRate(), billing, auditorAware.currentAuditorId(), clock.instant());
        return view(tabs.save(tab));
    }

    /**
     * Registers a payment through the tab, or answers the one already registered under the key.
     *
     * @throws TabNotFoundException if the tab does not exist
     */
    @Transactional
    public TabPaymentView receivePayment(TabId tabId, PaymentMethod method, Money amount, String idempotencyKey) {
        Tab tab = loadShared(tabId);
        ReceivedPaymentView payment = tab.receivePayment(method, amount, idempotencyKey, billing);
        return new TabPaymentView(payment, view(tab));
    }

    /** @throws TabNotFoundException if the tab does not exist */
    @Transactional
    public Tab reopen(TabId tabId, String reason) {
        Tab tab = loadExclusive(tabId);
        tab.reopen(reason, billing);
        return tabs.save(tab);
    }

    /** @throws TabNotFoundException if the tab does not exist */
    @Transactional
    public Tab close(TabId tabId) {
        Tab tab = loadExclusive(tabId);
        tab.close(billing, auditorAware.currentAuditorId(), clock.instant());
        return tabs.save(tab);
    }

    private TabClosingView view(Tab tab) {
        return new TabClosingView(
                tab,
                tab.bill(currentRate()),
                tab.folioId().map(billing::paidOn),
                tab.folioId().map(billing::balanceOf));
    }

    /**
     * The rate of the service charge in the setting now, for the responses that show an open tab's
     * live total (decision D20).
     *
     * @throws br.com.castel.sharedkernel.SettingNotFoundException if the setting is not configured
     */
    public Percentage currentServiceChargeRate() {
        return currentRate();
    }

    private Percentage currentRate() {
        return settings.asPercentage(SERVICE_CHARGE_PERCENT_SETTING);
    }

    private Tab loadShared(TabId tabId) {
        return tabs.findByIdForItemEntry(tabId).orElseThrow(() -> notFound(tabId));
    }

    private Tab loadExclusive(TabId tabId) {
        return tabs.findByIdForStatusChange(tabId).orElseThrow(() -> notFound(tabId));
    }

    private static TabNotFoundException notFound(TabId tabId) {
        return new TabNotFoundException("No tab " + tabId.value());
    }
}
