package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.FORTALEZA;
import static br.com.castel.restaurant.domain.TabFixtures.MENU_ITEM_UNAVAILABLE;
import static br.com.castel.restaurant.domain.TabFixtures.PROPERTY_ID;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.activeTable;
import static br.com.castel.restaurant.domain.TabFixtures.assertRejectedWith;
import static br.com.castel.restaurant.domain.TabFixtures.buffet;
import static br.com.castel.restaurant.domain.TabFixtures.pizza;
import static br.com.castel.restaurant.domain.TabFixtures.tabInStatus;
import static br.com.castel.restaurant.domain.TabFixtures.wednesdayAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.restaurant.api.MenuCategoryId;
import br.com.castel.restaurant.api.MenuItemId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The customer's order from the QR code of the table (task qr-table-ordering): born {@code PENDING},
 * answered once by a waiter, expired after 30 minutes without an answer (decisions #11, #14, #20).
 */
class TableOrderRequestTest {

    private static final String INVALID_TABLE_ORDER_REQUEST = "INVALID_TABLE_ORDER_REQUEST";
    private static final String TAB_CLOSING_REFUSES_ORDER_REQUEST = "TAB_CLOSING_REFUSES_ORDER_REQUEST";
    private static final String NOT_PENDING = "TABLE_ORDER_REQUEST_NOT_PENDING";
    private static final String EXPIRED = "TABLE_ORDER_REQUEST_EXPIRED";
    private static final String MENU_ITEM_NOT_FOUND = "MENU_ITEM_NOT_FOUND";

    private static final Instant REQUESTED_AT = wednesdayAt("12:00");
    private static final Instant WINDOW_END = REQUESTED_AT.plus(Duration.ofMinutes(30));
    private static final Instant WITHIN_WINDOW = wednesdayAt("12:10");
    private static final Instant AFTER_WINDOW = wednesdayAt("12:31");
    private static final UUID OTHER_WAITER = UUID.randomUUID();
    private static final String CUSTOMER_NAME = "Ana";
    private static final String DEVICE_ID = "phone-of-ana-0001";
    private static final String INVALID_CUSTOMER_NAME = "INVALID_CUSTOMER_NAME";
    private static final String INVALID_CUSTOMER_DEVICE_ID = "INVALID_CUSTOMER_DEVICE_ID";

    private final InMemoryMenuItems menuItems = new InMemoryMenuItems();
    private final MenuItem pizza = menuItems.add(pizza());
    private final DiningTable table = activeTable();

    private TableOrderRequest submit(List<TableOrderRequestLine> lines) {
        return TableOrderRequest.submit(PROPERTY_ID, table, CUSTOMER_NAME, DEVICE_ID, Optional.empty(), lines,
                menuItems, REQUESTED_AT, FORTALEZA);
    }

    private TableOrderRequest submitBy(String customerName, String customerDeviceId) {
        return TableOrderRequest.submit(PROPERTY_ID, table, customerName, customerDeviceId, Optional.empty(),
                List.of(lineOf(pizza)), menuItems, REQUESTED_AT, FORTALEZA);
    }

    private TableOrderRequest pending() {
        return submit(List.of(lineOf(pizza)));
    }

    private static TableOrderRequestLine lineOf(MenuItem item) {
        return TableOrderRequestLine.of(item.id(), null, 1, List.of(), null);
    }

    private static List<TableOrderRequestLine> linesOf(MenuItem item, int count) {
        List<TableOrderRequestLine> lines = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            lines.add(lineOf(item));
        }
        return lines;
    }

    private TableOrderRequest inStatus(TableOrderRequestStatus status) {
        TableOrderRequest request = pending();
        switch (status) {
            case CONFIRMED -> request.confirm(WAITER, WITHIN_WINDOW, TabId.newId());
            case REJECTED -> request.reject(WAITER, WITHIN_WINDOW);
            case EXPIRED -> request.expire(AFTER_WINDOW);
            case PENDING -> { }
        }
        return request;
    }

    // ------------------------------------------------------------------ submission

    @Nested
    class Submission {

        @Test
        void shouldBeBornPendingOnTheTableWithAThirtyMinuteWindow() {
            TableOrderRequest request = pending();

            assertThat(request.status()).isEqualTo(TableOrderRequestStatus.PENDING);
            assertThat(request.diningTableId()).isEqualTo(table.id());
            assertThat(request.requestedAt()).isEqualTo(REQUESTED_AT);
            assertThat(request.expiresAt()).isEqualTo(WINDOW_END);
            assertThat(request.resolvedAt()).isEmpty();
            assertThat(request.resolvedBy()).isEmpty();
            assertThat(request.tabId()).isEmpty();
        }

        @Test
        void shouldRefuseARequestWithoutLines() {
            assertRejectedWith(() -> submit(List.of()), INVALID_TABLE_ORDER_REQUEST);
        }

        @Test
        void shouldAcceptExactlyTwentyLines() {
            TableOrderRequest request = submit(linesOf(pizza, 20));

            assertThat(request.lines()).hasSize(20);
        }

        @Test
        void shouldRefuseTwentyOneLines() {
            assertRejectedWith(() -> submit(linesOf(pizza, 21)), INVALID_TABLE_ORDER_REQUEST);
        }

        @Test
        void shouldRefuseAnItemSoldByWeightSinceTheCustomerHasNoScale() {
            MenuItem buffet = menuItems.add(buffet());

            assertRejectedWith(() -> submit(List.of(lineOf(buffet))), INVALID_TABLE_ORDER_REQUEST);
        }

        @Test
        void shouldRefuseAnItemThatDoesNotExist() {
            TableOrderRequestLine ghost = TableOrderRequestLine.of(MenuItemId.newId(), null, 1, List.of(), null);

            assertRejectedWith(() -> submit(List.of(ghost)), MENU_ITEM_NOT_FOUND);
        }

        @Test
        void shouldRefuseAnItemThatRanOut() {
            pizza.markUnavailable();

            assertRejectedWith(() -> submit(List.of(lineOf(pizza))), MENU_ITEM_UNAVAILABLE);
        }

        @Test
        void shouldRefuseWhenTheTabOfTheTableIsClosing() {
            Optional<Tab> closing = Optional.of(tabInStatus(TabStatus.CLOSING, new FakeTabBilling()));

            assertRejectedWith(
                    () -> TableOrderRequest.submit(PROPERTY_ID, table, CUSTOMER_NAME, DEVICE_ID, closing,
                            List.of(lineOf(pizza)), menuItems, REQUESTED_AT, FORTALEZA),
                    TAB_CLOSING_REFUSES_ORDER_REQUEST);
        }

        @Test
        void shouldAcceptWhenTheTabOfTheTableIsOpen() {
            Optional<Tab> open = Optional.of(tabInStatus(TabStatus.OPEN, new FakeTabBilling()));

            TableOrderRequest request = TableOrderRequest.submit(PROPERTY_ID, table, CUSTOMER_NAME, DEVICE_ID, open,
                    List.of(lineOf(pizza)), menuItems, REQUESTED_AT, FORTALEZA);

            assertThat(request.status()).isEqualTo(TableOrderRequestStatus.PENDING);
        }

        @Test
        void shouldNotTellThePhoneToOrderWhileTheTabIsClosing() {
            assertThat(TableOrderRequest.acceptsOrdersWith(Optional.empty())).isTrue();
            assertThat(TableOrderRequest.acceptsOrdersWith(
                    Optional.of(tabInStatus(TabStatus.CLOSING, new FakeTabBilling())))).isFalse();
        }

        @Test
        void shouldExposeTheLinesReadOnly() {
            TableOrderRequest request = pending();

            assertThatThrownBy(() -> request.lines().add(lineOf(pizza)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ------------------------------------------------------------------ answering inside the window

    @Nested
    class AnsweringPending {

        @Test
        void shouldConfirmRecordingTheWaiterTheMomentAndTheTab() {
            TableOrderRequest request = pending();
            TabId tabId = TabId.newId();

            request.confirm(WAITER, WITHIN_WINDOW, tabId);

            assertThat(request.status()).isEqualTo(TableOrderRequestStatus.CONFIRMED);
            assertThat(request.resolvedBy()).contains(WAITER);
            assertThat(request.resolvedAt()).contains(WITHIN_WINDOW);
            assertThat(request.tabId()).contains(tabId);
        }

        @Test
        void shouldRejectRecordingTheWaiterAndNoTab() {
            TableOrderRequest request = pending();

            request.reject(WAITER, WITHIN_WINDOW);

            assertThat(request.status()).isEqualTo(TableOrderRequestStatus.REJECTED);
            assertThat(request.resolvedBy()).contains(WAITER);
            assertThat(request.resolvedAt()).contains(WITHIN_WINDOW);
            assertThat(request.tabId()).isEmpty();
        }

        @Test
        void shouldExpireAfterTheWindowWithNoAuthor() {
            TableOrderRequest request = pending();

            request.expire(AFTER_WINDOW);

            assertThat(request.status()).isEqualTo(TableOrderRequestStatus.EXPIRED);
            assertThat(request.resolvedAt()).contains(AFTER_WINDOW);
            assertThat(request.resolvedBy()).isEmpty();
            assertThat(request.tabId()).isEmpty();
        }

        @Test
        void shouldExpireExactlyAtTheEndOfTheWindow() {
            TableOrderRequest request = pending();

            request.expire(WINDOW_END);

            assertThat(request.status()).isEqualTo(TableOrderRequestStatus.EXPIRED);
        }

        @Test
        void shouldRefuseToExpireARequestStillInsideItsWindow() {
            TableOrderRequest request = pending();

            assertThatThrownBy(() -> request.expire(WINDOW_END.minusNanos(1)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(request.status()).isEqualTo(TableOrderRequestStatus.PENDING);
        }

        @Test
        void shouldLetAPendingRequestInsideItsWindowBeAnswered() {
            TableOrderRequest request = pending();

            request.requireAnswerableAt(WITHIN_WINDOW);

            assertThat(request.status()).isEqualTo(TableOrderRequestStatus.PENDING);
        }
    }

    // ------------------------------------------------------------------ resolved is final

    @Nested
    class ResolvedIsFinal {

        @ParameterizedTest
        @EnumSource(value = TableOrderRequestStatus.class, names = {"PENDING", "EXPIRED"}, mode = EnumSource.Mode.EXCLUDE)
        void shouldRefuseConfirmationWhenRequestIsNotPending(TableOrderRequestStatus status) {
            TableOrderRequest request = inStatus(status);

            assertRejectedWith(() -> request.confirm(OTHER_WAITER, WITHIN_WINDOW, TabId.newId()), NOT_PENDING);
            assertThat(request.status()).isEqualTo(status);
        }

        @ParameterizedTest
        @EnumSource(value = TableOrderRequestStatus.class, names = {"PENDING", "EXPIRED"}, mode = EnumSource.Mode.EXCLUDE)
        void shouldRefuseRejectionWhenRequestIsNotPending(TableOrderRequestStatus status) {
            TableOrderRequest request = inStatus(status);

            assertRejectedWith(() -> request.reject(OTHER_WAITER, WITHIN_WINDOW), NOT_PENDING);
            assertThat(request.status()).isEqualTo(status);
        }

        @ParameterizedTest
        @EnumSource(value = TableOrderRequestStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
        void shouldRefuseExpiryWhenRequestIsNotPending(TableOrderRequestStatus status) {
            TableOrderRequest request = inStatus(status);

            assertRejectedWith(() -> request.expire(AFTER_WINDOW.plusSeconds(60)), NOT_PENDING);
            assertThat(request.status()).isEqualTo(status);
        }

        @ParameterizedTest
        @EnumSource(value = TableOrderRequestStatus.class, names = {"PENDING", "EXPIRED"}, mode = EnumSource.Mode.EXCLUDE)
        void shouldReportNotPendingRatherThanExpiredWhenAResolvedRequestIsAnsweredLate(
                TableOrderRequestStatus status) {
            TableOrderRequest request = inStatus(status);

            assertRejectedWith(() -> request.requireAnswerableAt(AFTER_WINDOW), NOT_PENDING);
        }

        @Test
        void shouldReportExpiredWhenTheJobAlreadyStoredTheExpiry() {
            TableOrderRequest request = inStatus(TableOrderRequestStatus.EXPIRED);

            assertRejectedWith(() -> request.confirm(WAITER, WITHIN_WINDOW, TabId.newId()), EXPIRED);
            assertRejectedWith(() -> request.reject(WAITER, WITHIN_WINDOW), EXPIRED);
            assertThat(request.status()).isEqualTo(TableOrderRequestStatus.EXPIRED);
        }

        @Test
        void shouldKeepTheFirstAnswerWhenASecondWaiterConfirms() {
            TableOrderRequest request = pending();
            TabId firstTab = TabId.newId();
            request.confirm(WAITER, WITHIN_WINDOW, firstTab);

            assertRejectedWith(() -> request.confirm(OTHER_WAITER, WITHIN_WINDOW, TabId.newId()), NOT_PENDING);
            assertThat(request.resolvedBy()).contains(WAITER);
            assertThat(request.tabId()).contains(firstTab);
        }
    }

    // ------------------------------------------------------------------ the 30-minute window

    @Nested
    class Window {

        @Test
        void shouldNotBeExpiredOneNanosecondBeforeTheEndOfTheWindow() {
            assertThat(pending().isExpiredAt(WINDOW_END.minusNanos(1))).isFalse();
        }

        @Test
        void shouldBeExpiredExactlyAtTheEndOfTheWindow() {
            assertThat(pending().isExpiredAt(WINDOW_END)).isTrue();
        }

        @Test
        void shouldBeExpiredOneNanosecondAfterTheEndOfTheWindow() {
            assertThat(pending().isExpiredAt(WINDOW_END.plusNanos(1))).isTrue();
        }

        @Test
        void shouldReadAsExpiredOnceTheWindowIsOverEvenBeforeTheJobStoresIt() {
            TableOrderRequest request = pending();

            assertThat(request.statusAt(WINDOW_END.minusNanos(1))).isEqualTo(TableOrderRequestStatus.PENDING);
            assertThat(request.statusAt(WINDOW_END)).isEqualTo(TableOrderRequestStatus.EXPIRED);
            assertThat(request.status()).isEqualTo(TableOrderRequestStatus.PENDING);
        }

        @Test
        void shouldKeepReadingAConfirmedRequestAsConfirmedAfterTheWindow() {
            TableOrderRequest request = inStatus(TableOrderRequestStatus.CONFIRMED);

            assertThat(request.statusAt(AFTER_WINDOW)).isEqualTo(TableOrderRequestStatus.CONFIRMED);
            assertThat(request.isExpiredAt(AFTER_WINDOW)).isFalse();
        }

        @Test
        void shouldConfirmOneNanosecondBeforeTheEndOfTheWindow() {
            TableOrderRequest request = pending();

            request.confirm(WAITER, WINDOW_END.minusNanos(1), TabId.newId());

            assertThat(request.status()).isEqualTo(TableOrderRequestStatus.CONFIRMED);
        }

        @Test
        void shouldRefuseConfirmationExactlyAtTheEndOfTheWindow() {
            TableOrderRequest request = pending();

            assertRejectedWith(() -> request.confirm(WAITER, WINDOW_END, TabId.newId()), EXPIRED);
            assertThat(request.status()).isEqualTo(TableOrderRequestStatus.PENDING);
        }

        @Test
        void shouldRefuseConfirmationOfAnOverdueRequest() {
            TableOrderRequest request = pending();

            assertRejectedWith(() -> request.confirm(WAITER, AFTER_WINDOW, TabId.newId()), EXPIRED);
            assertThat(request.tabId()).isEmpty();
        }

        @Test
        void shouldRefuseRejectionOfAnOverdueRequest() {
            TableOrderRequest request = pending();

            assertRejectedWith(() -> request.reject(WAITER, AFTER_WINDOW), EXPIRED);
            assertThat(request.status()).isEqualTo(TableOrderRequestStatus.PENDING);
        }

        @Test
        void shouldTellTheUseCaseAnOverdueRequestCanNoLongerBeAnswered() {
            TableOrderRequest request = pending();

            assertRejectedWith(() -> request.requireAnswerableAt(AFTER_WINDOW), EXPIRED);
        }
    }

    // ------------------------------------------------------------------ the customer (#46 of qr-table-payment)

    @Nested
    class Customer {

        @Test
        void shouldKeepTheTrimmedNameAndTheDeviceOfTheCustomer() {
            TableOrderRequest request = submitBy("  Ana  ", DEVICE_ID);

            assertThat(request.customerName()).contains("Ana");
            assertThat(request.customerDeviceId()).contains(DEVICE_ID);
        }

        @Test
        void shouldAcceptANameOfFortyCharacters() {
            assertThat(TableOrderRequest.requireCustomerName("a".repeat(40))).hasSize(40);
        }

        @Test
        void shouldRefuseANameOfFortyOneCharacters() {
            assertRejectedWith(() -> submitBy("a".repeat(41), DEVICE_ID), INVALID_CUSTOMER_NAME);
        }

        @Test
        void shouldRefuseABlankName() {
            assertRejectedWith(() -> submitBy(" ", DEVICE_ID), INVALID_CUSTOMER_NAME);
        }

        @Test
        void shouldRefuseAMissingName() {
            assertRejectedWith(() -> submitBy(null, DEVICE_ID), INVALID_CUSTOMER_NAME);
        }

        @Test
        void shouldAcceptADeviceCodeOfEightCharacters() {
            assertThat(TableOrderRequest.requireCustomerDeviceId("abcd-_12")).isEqualTo("abcd-_12");
        }

        @Test
        void shouldRefuseADeviceCodeOfSevenCharacters() {
            assertRejectedWith(() -> submitBy(CUSTOMER_NAME, "abcd-12"), INVALID_CUSTOMER_DEVICE_ID);
        }

        @Test
        void shouldAcceptADeviceCodeOfSixtyFourCharacters() {
            assertThat(TableOrderRequest.requireCustomerDeviceId("Z".repeat(64))).hasSize(64);
        }

        @Test
        void shouldRefuseADeviceCodeOfSixtyFiveCharacters() {
            assertRejectedWith(() -> submitBy(CUSTOMER_NAME, "Z".repeat(65)), INVALID_CUSTOMER_DEVICE_ID);
        }

        @Test
        void shouldRefuseADeviceCodeWithASpace() {
            assertRejectedWith(() -> submitBy(CUSTOMER_NAME, "abcd efgh"), INVALID_CUSTOMER_DEVICE_ID);
        }

        @Test
        void shouldRefuseAMissingDeviceCode() {
            assertRejectedWith(() -> submitBy(CUSTOMER_NAME, null), INVALID_CUSTOMER_DEVICE_ID);
        }
    }

    // ------------------------------------------------------------------ in-memory menu

    /** A plain in-memory menu: the domain reads items through this port, and the items are pure domain. */
    private static final class InMemoryMenuItems implements MenuItemRepository {

        private final Map<MenuItemId, MenuItem> items = new HashMap<>();

        MenuItem add(MenuItem item) {
            items.put(item.id(), item);
            return item;
        }

        @Override
        public Optional<MenuItem> findById(MenuItemId id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public boolean existsByName(UUID propertyId, String name) {
            return false;
        }

        @Override
        public List<MenuItem> findAllOrdered(UUID propertyId) {
            return Collections.emptyList();
        }

        @Override
        public List<MenuItem> findActiveOrdered(UUID propertyId) {
            return Collections.emptyList();
        }

        @Override
        public List<MenuItem> findActiveOrderedByCategory(UUID propertyId, MenuCategoryId categoryId) {
            return Collections.emptyList();
        }

        @Override
        public MenuItem save(MenuItem item) {
            return add(item);
        }
    }
}
