package br.com.castel.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.PrepStation;
import br.com.castel.sharedkernel.Money;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The invariants of the menu item, which is where a mistake costs money: an item priced the wrong
 * way, or served at the wrong hour.
 */
class MenuItemTest {

    private static final ZoneId FORTALEZA = ZoneId.of("America/Fortaleza");
    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final MenuCategoryId CATEGORY_ID = MenuCategoryId.newId();

    private static MenuItem pizza() {
        return MenuItem.soldByUnit(PROPERTY_ID, CATEGORY_ID, "Pizza Margherita", PrepStation.PIZZA, Money.of("62.00"));
    }

    private static MenuItem buffet() {
        return MenuItem.soldByWeight(PROPERTY_ID, CATEGORY_ID, "Buffet", PrepStation.KITCHEN, Money.of("89.90"));
    }

    /** A moment of a Wednesday, at the given local time in the property's zone. */
    private static Instant wednesdayAt(String localTime) {
        return LocalDateTime.of(2026, 9, 16, 0, 0).with(LocalTime.parse(localTime)).atZone(FORTALEZA).toInstant();
    }

    @Nested
    @DisplayName("pricing")
    class Pricing {

        @Test
        void shouldChargeAnItemSoldByWeightPerKilo() {
            MenuItem item = buffet();

            assertThat(item.soldByWeight()).isTrue();
            assertThat(item.price()).isEqualTo(Money.of("89.90"));
        }

        @Test
        void shouldRejectAnItemSoldByUnitWithoutAPrice() {
            assertThatThrownBy(() ->
                            MenuItem.soldByUnit(PROPERTY_ID, CATEGORY_ID, "Pizza", PrepStation.PIZZA, null))
                    .isInstanceOf(InvalidMenuItemPricingException.class);
        }

        @Test
        void shouldRejectAnItemSoldByWeightWithoutAPricePerKilo() {
            assertThatThrownBy(() ->
                            MenuItem.soldByWeight(PROPERTY_ID, CATEGORY_ID, "Buffet", PrepStation.KITCHEN, null))
                    .isInstanceOf(InvalidMenuItemPricingException.class);
        }

        @Test
        void shouldRejectAPriceThatIsNotPositive() {
            assertThatThrownBy(() -> MenuItem.soldByUnit(
                            PROPERTY_ID, CATEGORY_ID, "Brinde", PrepStation.BAR, Money.of("0.00")))
                    .isInstanceOf(InvalidMenuItemPricingException.class);
        }

        @Test
        void shouldKeepSellingByWeightWhenThePriceChanges() {
            MenuItem item = buffet();

            item.changePriceTo(Money.of("99.90"));

            assertThat(item.soldByWeight()).isTrue();
            assertThat(item.price()).isEqualTo(Money.of("99.90"));
        }

        @Test
        void shouldRejectABlankName() {
            assertThatThrownBy(() ->
                            MenuItem.soldByUnit(PROPERTY_ID, CATEGORY_ID, "  ", PrepStation.BAR, Money.of("5.00")))
                    .isInstanceOf(InvalidMenuItemNameException.class);
        }
    }

    @Nested
    @DisplayName("availability")
    class Availability {

        @Test
        void shouldServeAnItemWithoutWindowsAtAnyHour() {
            MenuItem item = pizza();

            assertThat(item.isAvailableAt(wednesdayAt("03:00"), FORTALEZA)).isTrue();
            assertThat(item.isAvailableAt(wednesdayAt("15:00"), FORTALEZA)).isTrue();
        }

        @Test
        void shouldNotServeOneMinuteBeforeTheWindowOpens() {
            MenuItem item = pizza();
            item.serveBetween(null, LocalTime.of(18, 30), LocalTime.of(23, 0));

            assertThat(item.isAvailableAt(wednesdayAt("18:29"), FORTALEZA)).isFalse();
        }

        @Test
        void shouldServeOneMinuteAfterTheWindowOpens() {
            MenuItem item = pizza();
            item.serveBetween(null, LocalTime.of(18, 30), LocalTime.of(23, 0));

            assertThat(item.isAvailableAt(wednesdayAt("18:31"), FORTALEZA)).isTrue();
        }

        @Test
        void shouldServeOnBothSidesOfMidnightWhenTheWindowCrossesIt() {
            MenuItem item = pizza();
            item.serveBetween(null, LocalTime.of(22, 0), LocalTime.of(2, 0));

            assertThat(item.isAvailableAt(wednesdayAt("23:30"), FORTALEZA)).isTrue();
            assertThat(item.isAvailableAt(wednesdayAt("01:00"), FORTALEZA)).isTrue();
            assertThat(item.isAvailableAt(wednesdayAt("12:00"), FORTALEZA)).isFalse();
        }

        @Test
        void shouldServeOnlyOnTheDayTheWindowNames() {
            MenuItem item = pizza();
            item.serveBetween(DayOfWeek.WEDNESDAY, LocalTime.of(18, 30), LocalTime.of(23, 0));

            assertThat(item.isAvailableAt(wednesdayAt("20:00"), FORTALEZA)).isTrue();
            assertThat(item.isAvailableAt(wednesdayAt("20:00").plusSeconds(86_400), FORTALEZA))
                    .isFalse();
        }

        @Test
        void shouldNotServeWhatTheKitchenRanOutOfEvenInsideTheWindow() {
            MenuItem item = pizza();
            item.serveBetween(null, LocalTime.of(18, 30), LocalTime.of(23, 0));

            item.markUnavailable();

            assertThat(item.isAvailableAt(wednesdayAt("20:00"), FORTALEZA)).isFalse();
        }

        @Test
        void shouldNotServeAnItemOutOfTheMenu() {
            MenuItem item = pizza();

            item.deactivate();

            assertThat(item.isAvailableAt(wednesdayAt("20:00"), FORTALEZA)).isFalse();
        }

        @Test
        void shouldReadTheScheduleInThePropertysZoneAndNotTheServers() {
            MenuItem item = pizza();
            item.serveBetween(null, LocalTime.of(18, 30), LocalTime.of(23, 0));
            Instant eightPmInFortaleza = wednesdayAt("20:00");

            assertThat(item.isAvailableAt(eightPmInFortaleza, FORTALEZA)).isTrue();
            assertThat(item.isAvailableAt(eightPmInFortaleza, ZoneId.of("UTC"))).isFalse();
        }

        @Test
        void shouldRejectAWindowThatStartsAndEndsAtTheSameTime() {
            MenuItem item = pizza();

            assertThatThrownBy(() -> item.serveBetween(null, LocalTime.of(18, 0), LocalTime.of(18, 0)))
                    .isInstanceOf(InvalidAvailabilityWindowException.class);
        }

        @Test
        void shouldGoBackToAnyHourWhenTheWindowsAreCleared() {
            MenuItem item = pizza();
            item.serveBetween(null, LocalTime.of(18, 30), LocalTime.of(23, 0));

            item.serveAtAnyHour();

            assertThat(item.isAvailableAt(wednesdayAt("03:00"), FORTALEZA)).isTrue();
        }
    }
}
