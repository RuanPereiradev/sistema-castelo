package br.com.castel.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * Shared arrangement for the tab tests of task 2.2. Every moment is fixed and read in the zone of the
 * property, so a schedule check never depends on the machine running the build.
 */
final class TabFixtures {

    static final ZoneId FORTALEZA = ZoneId.of("America/Fortaleza");
    static final UUID PROPERTY_ID = UUID.fromString("7a1c3c1e-0000-4000-8000-000000000001");
    static final MenuCategoryId CATEGORY_ID = MenuCategoryId.newId();
    static final UUID WAITER = UUID.fromString("7a1c3c1e-0000-4000-8000-0000000000a1");
    static final UUID OTHER_WAITER = UUID.fromString("7a1c3c1e-0000-4000-8000-0000000000a2");

    /** 2026-09-16 is a Wednesday. */
    static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 16);

    static final LocalDate THURSDAY = WEDNESDAY.plusDays(1);
    static final Instant OPENED_AT = wednesdayAt("12:00");
    static final Instant ORDERED_AT = wednesdayAt("12:05");
    static final Instant CANCELLED_AT = wednesdayAt("12:30");

    // ------------------------------------------------------------------ codes, section 5

    static final String TAB_NOT_OPEN = "TAB_NOT_OPEN";
    static final String TAB_ITEM_NOT_FOUND = "TAB_ITEM_NOT_FOUND";
    static final String TAB_ITEM_ALREADY_CANCELLED = "TAB_ITEM_ALREADY_CANCELLED";
    static final String TAB_HAS_ACTIVE_ITEMS = "TAB_HAS_ACTIVE_ITEMS";
    static final String INVALID_CARD_NUMBER = "INVALID_CARD_NUMBER";
    static final String INACTIVE_DINING_TABLE = "INACTIVE_DINING_TABLE";
    static final String MENU_ITEM_UNAVAILABLE = "MENU_ITEM_UNAVAILABLE";
    static final String OUTSIDE_WINDOW = "MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW";
    static final String VARIANT_REQUIRED = "MENU_ITEM_VARIANT_REQUIRED";
    static final String VARIANT_UNAVAILABLE = "MENU_ITEM_VARIANT_UNAVAILABLE";
    static final String VARIANT_NOT_FOUND = "MENU_ITEM_VARIANT_NOT_FOUND";
    static final String REQUIRES_WEIGHT = "SOLD_BY_WEIGHT_REQUIRES_WEIGHT";
    static final String WEIGHT_REJECTS_QUANTITY = "SOLD_BY_WEIGHT_REJECTS_QUANTITY";
    static final String WEIGHT_REJECTS_VARIANT = "SOLD_BY_WEIGHT_REJECTS_VARIANT";
    static final String WEIGHT_REJECTS_MODIFIER = "SOLD_BY_WEIGHT_REJECTS_MODIFIER";
    static final String UNIT_REJECTS_WEIGHT = "SOLD_BY_UNIT_REJECTS_WEIGHT";
    static final String MODIFIER_NOT_OFFERED = "MODIFIER_NOT_OFFERED";
    static final String INACTIVE_MODIFIER = "INACTIVE_MODIFIER";
    static final String INVALID_MODIFIER_QUANTITY = "INVALID_TAB_ITEM_MODIFIER_QUANTITY";
    static final String DUPLICATE_MODIFIER = "DUPLICATE_TAB_ITEM_MODIFIER";
    static final String INVALID_SPECIAL_INSTRUCTIONS = "INVALID_SPECIAL_INSTRUCTIONS";
    static final String INVALID_CANCELLATION_REASON = "INVALID_CANCELLATION_REASON";
    static final String INVALID_QUANTITY = "INVALID_QUANTITY";
    static final String INVALID_WEIGHT = "INVALID_WEIGHT";

    private TabFixtures() {
    }

    // ------------------------------------------------------------------ moments

    static Instant wednesdayAt(String localTime) {
        return at(WEDNESDAY, localTime);
    }

    static Instant at(LocalDate day, String localTime) {
        return day.atTime(LocalTime.parse(localTime)).atZone(FORTALEZA).toInstant();
    }

    // ------------------------------------------------------------------ tabs

    static DiningTable activeTable() {
        return DiningTable.create(PROPERTY_ID, "M1", 4, "Salão");
    }

    static Tab tableTab() {
        return Tab.openForTable(PROPERTY_ID, activeTable(), WAITER, OPENED_AT);
    }

    static Tab cardTab() {
        return Tab.openForSelfService(PROPERTY_ID, 42, WAITER, OPENED_AT);
    }

    /** An empty tab cancelled by mistake: the only status other than OPEN a tab reaches in 2.2. */
    static Tab cancelledTab() {
        Tab tab = tableTab();
        tab.cancel("Aberta na mesa errada", WAITER, CANCELLED_AT);
        return tab;
    }

    // ------------------------------------------------------------------ menu

    static MenuItem pizza() {
        return MenuItem.soldByUnit(PROPERTY_ID, CATEGORY_ID, "Pizza Calabresa", PrepStation.PIZZA, Money.of("62.00"));
    }

    static MenuItem steak() {
        return MenuItem.soldByUnit(PROPERTY_ID, CATEGORY_ID, "Picanha", PrepStation.KITCHEN, Money.of("89.00"));
    }

    static MenuItem beer() {
        return MenuItem.soldByUnit(PROPERTY_ID, CATEGORY_ID, "Cerveja", PrepStation.BAR, Money.of("12.00"));
    }

    static MenuItem buffet() {
        return MenuItem.soldByWeight(PROPERTY_ID, CATEGORY_ID, "Buffet", PrepStation.KITCHEN, Money.of("59.90"));
    }

    static Modifier stuffedCrust() {
        return Modifier.create(PROPERTY_ID, "Borda recheada", Money.of("8.50"));
    }

    static Modifier doneness() {
        return Modifier.create(PROPERTY_ID, "Ponto da carne", Money.ZERO);
    }

    // ------------------------------------------------------------------ orders

    static TabItemOrder oneUnit() {
        return new TabItemOrder(null, null, null, List.of(), null);
    }

    static TabItemOrder units(Integer quantity) {
        return new TabItemOrder(null, quantity, null, List.of(), null);
    }

    static TabItemOrder grams(Integer weightGrams) {
        return new TabItemOrder(null, null, weightGrams, List.of(), null);
    }

    static TabItemOrder ofVariant(MenuItemVariantId variantId) {
        return new TabItemOrder(variantId, null, null, List.of(), null);
    }

    static TabItemOrder withModifiers(ModifierChoice... choices) {
        return new TabItemOrder(null, null, null, List.of(choices), null);
    }

    static TabItemOrder withInstructions(String specialInstructions) {
        return new TabItemOrder(null, null, null, List.of(), specialInstructions);
    }

    static TabItem order(Tab tab, MenuItem menuItem, TabItemOrder order) {
        return tab.addItem(menuItem, order, WAITER, ORDERED_AT, FORTALEZA);
    }

    // ------------------------------------------------------------------ asserts

    static void assertRejectedWith(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(DomainException.class, failure -> assertThat(failure.code()).isEqualTo(code));
    }
}
