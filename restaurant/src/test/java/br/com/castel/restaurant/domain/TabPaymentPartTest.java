package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.CLOSED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.CLOSING_AT;
import static br.com.castel.restaurant.domain.TabFixtures.MERGED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.PROPERTY_ID;
import static br.com.castel.restaurant.domain.TabFixtures.TAB_NOT_OPEN;
import static br.com.castel.restaurant.domain.TabFixtures.TEN_PERCENT;
import static br.com.castel.restaurant.domain.TabFixtures.TRANSFERRED_AT;
import static br.com.castel.restaurant.domain.TabFixtures.TWENTY_PERCENT;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.activeTable;
import static br.com.castel.restaurant.domain.TabFixtures.assertRejectedWith;
import static br.com.castel.restaurant.domain.TabFixtures.buffet;
import static br.com.castel.restaurant.domain.TabFixtures.grams;
import static br.com.castel.restaurant.domain.TabFixtures.order;
import static br.com.castel.restaurant.domain.TabFixtures.orderDish;
import static br.com.castel.restaurant.domain.TabFixtures.tabInStatus;
import static br.com.castel.restaurant.domain.TabFixtures.tableTab;
import static br.com.castel.restaurant.domain.TabFixtures.dish;
import static br.com.castel.restaurant.domain.TabFixtures.units;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.AFTER_WINDOW;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.CONFIRMED_AT;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.CONFIRMED_WHILE_CLOSING_AT;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.CUSTOMER_NAME;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.DEVICE_ID;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.INVALID_TABLE_PAYMENT_REQUEST;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.NOT_REFUNDABLE;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.PIX_RECEIVER_NOT_CONFIGURED;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.REFUNDED_AT;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.REFUND_REASON;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.REQUESTED_AT;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.REQUESTED_WHILE_CLOSING_AT;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.TAB_ITEM_ALREADY_PAID;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.TAB_ITEM_RESERVED_FOR_PAYMENT;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.request;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.requestAt;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.choice;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.requestUnits;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.whole;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.sharedkernel.Money;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The money of a tab once the phone of the table pays parts of it (task qr-table-payment): the frozen
 * amount of a part, the items it locks, and how the closing, the reopening and the refund keep the
 * folio equal to the parts paid plus one closing charge over the items not paid (decisions #10, #17,
 * #19, #20, #21, #25, #26, #28, #29).
 *
 * <p>The running example: 100.00 + 33.35 at 10% is 146.69, as in task 3.2. The phone pays the 33.35,
 * which costs 33.35 + 3.34 (3.335 half up) = 36.69, and leaves 100.00 + 10.00 = 110.00 due.
 */
class TabPaymentPartTest {

    private final FakeTabBilling billing = new FakeTabBilling();
    private final Tab tab = tableTab();
    private final TabItem hundred = orderDish(tab, "100.00");
    private final TabItem small = orderDish(tab, "33.35");

    private TablePaymentRequest paySmall() {
        TablePaymentRequest request = request(tab, small);
        request.confirm(tab, billing, WAITER, CONFIRMED_AT);
        return request;
    }

    private void startClosing() {
        tab.startClosing(TEN_PERCENT, billing, WAITER, CLOSING_AT);
    }

    private FolioId folio() {
        return tab.folioId().orElseThrow();
    }

    // ------------------------------------------------------------------ the amount of a part

    @Nested
    class AmountOfAPart {

        @Test
        void shouldChargeTheItemsPlusTheServiceChargeOverThemAtTheCurrentRate() {
            TablePaymentRequest request = request(tab, small);

            assertThat(request.subtotal()).isEqualTo(Money.of("33.35"));
            assertThat(request.serviceCharge()).isEqualTo(Money.of("3.34"));
            assertThat(request.amount()).isEqualTo(Money.of("36.69"));
            assertThat(request.serviceChargeRate()).isEqualTo(TEN_PERCENT);
        }

        @Test
        void shouldLeaveTheServiceChargeOutForAnItemTheWaiterWaived() {
            tab.removeServiceChargeFrom(hundred.id());

            TablePaymentRequest request = request(tab, hundred, small);

            assertThat(request.subtotal()).isEqualTo(Money.of("133.35"));
            assertThat(request.serviceCharge()).isEqualTo(Money.of("3.34"));
            assertThat(request.amount()).isEqualTo(Money.of("136.69"));
        }

        @Test
        void shouldRoundTheChargeOnceAndShareItAmongTheItemsByTheLargestRemainder() {
            TabItem first = orderDish(tab, "10.05");
            TabItem second = orderDish(tab, "10.05");

            TablePaymentRequest request = request(tab, first, second);

            assertThat(request.serviceCharge()).isEqualTo(Money.of("2.01"));
            assertThat(request.items()).extracting(TablePaymentRequestItem::serviceCharge)
                    .containsExactlyInAnyOrder(Money.of("1.00"), Money.of("1.01"));
        }

        @Test
        void shouldAcceptAnItemSoldByWeight() {
            TabItem plate = order(tab, buffet(), grams(437));

            TablePaymentRequest request = request(tab, plate);

            assertThat(request.subtotal()).isEqualTo(Money.of("26.18"));
            assertThat(plate.isReservedForPayment()).isTrue();
        }

        @Test
        void shouldKeepTheRateOfAPaidPartWhenTheRateOfTheTableChangesLater() {
            paySmall();

            assertThat(tab.serviceCharge(TWENTY_PERCENT)).isEqualTo(Money.of("23.34"));
            assertThat(tab.total(TWENTY_PERCENT)).isEqualTo(Money.of("156.69"));
            assertThat(tab.paidAmount()).isEqualTo(Money.of("36.69"));
            assertThat(tab.amountDue(TWENTY_PERCENT)).isEqualTo(Money.of("120.00"));
        }

        @Test
        void shouldPriceAPartAtTheFrozenRateWhileTheTabIsClosing() {
            startClosing();

            TablePaymentRequest request = requestAt(tab, TWENTY_PERCENT, REQUESTED_WHILE_CLOSING_AT, small);

            assertThat(request.serviceCharge()).isEqualTo(Money.of("3.34"));
            assertThat(request.serviceChargeRate()).isEqualTo(TEN_PERCENT);
        }

        @Test
        void shouldFreezeTheShareOfTheChargeOnTheItemPaid() {
            paySmall();

            assertThat(small.isPaid()).isTrue();
            assertThat(small.isReservedForPayment()).isFalse();
            assertThat(small.paidServiceCharge()).isEqualTo(Money.of("3.34"));
        }
    }

    // ------------------------------------------------------------------ total, paid and due add up

    @Nested
    class TotalsAddUp {

        @Test
        void shouldMatchTheNumbersOfTask32WhenNothingWasPaid() {
            TabBill bill = tab.bill(TEN_PERCENT);

            assertThat(bill.serviceCharge()).isEqualTo(Money.of("13.34"));
            assertThat(bill.total()).isEqualTo(Money.of("146.69"));
            assertThat(bill.paidAmount()).isEqualTo(Money.ZERO);
            assertThat(bill.amountDue()).isEqualTo(Money.of("146.69"));
        }

        @Test
        void shouldNotCountAReservedItemAsPaid() {
            request(tab, small);

            assertThat(tab.paidAmount()).isEqualTo(Money.ZERO);
            assertThat(tab.amountDue(TEN_PERCENT)).isEqualTo(Money.of("146.69"));
        }

        @Test
        void shouldTakeTheConfirmedPartOffWhatIsDue() {
            paySmall();

            TabBill bill = tab.bill(TEN_PERCENT);
            assertThat(bill.total()).isEqualTo(Money.of("146.69"));
            assertThat(bill.paidAmount()).isEqualTo(Money.of("36.69"));
            assertThat(bill.amountDue()).isEqualTo(Money.of("110.00"));
        }

        /** The one-cent limitation of the decisions file: each part rounds on its own base. */
        @Test
        void shouldKeepTotalEqualToPaidPlusDueWhenEachPartRoundsOnItsOwn() {
            Tab coins = tableTab();
            TabItem first = orderDish(coins, "0.05");
            orderDish(coins, "0.05");
            TablePaymentRequest request = requestAt(coins, TEN_PERCENT, REQUESTED_AT, first);
            request.confirm(coins, billing, WAITER, CONFIRMED_AT);

            assertThat(coins.paidAmount()).isEqualTo(Money.of("0.06"));
            assertThat(coins.amountDue(TEN_PERCENT)).isEqualTo(Money.of("0.06"));
            assertThat(coins.total(TEN_PERCENT)).isEqualTo(Money.of("0.12"));
        }
    }

    // ------------------------------------------------------------------ part of the quantity (#39, #42, #44, #45)

    /**
     * A line of 3 × 3.35 = 10.05 joins the 100.00 and the 33.35: 143.40 at 10% is 157.74. Paying 2 of
     * the 3 units with the 33.35 costs 6.70 + 33.35 = 40.05 and 4.005 = 4.01 of service, shared 0.67
     * (6.70 has no remainder) and 3.34 (3.335 takes the cent left).
     */
    @Nested
    class PartOfTheQuantity {

        private final TabItem trio = order(tab, dish("3.35"), units(3));

        private TablePaymentRequest twoOfTrioWithSmall(boolean includeServiceCharge) {
            return requestUnits(tab, includeServiceCharge, choice(trio, 2), whole(small));
        }

        @Test
        void shouldPriceTheUnitsChosenAtTheLineTotalDividedByTheQuantity() {
            TablePaymentRequest request = requestUnits(tab, true, choice(trio, 2));

            assertThat(request.subtotal()).isEqualTo(Money.of("6.70"));
            assertThat(request.serviceCharge()).isEqualTo(Money.of("0.67"));
            assertThat(request.amount()).isEqualTo(Money.of("7.37"));
            assertThat(request.items()).singleElement().satisfies(item -> {
                assertThat(item.quantity()).isEqualTo(2);
                assertThat(item.lineTotal()).isEqualTo(Money.of("6.70"));
            });
        }

        @Test
        void shouldRoundTheServiceOfOneUnitHalfUp() {
            TablePaymentRequest request = requestUnits(tab, true, choice(trio, 1));

            assertThat(request.serviceCharge()).isEqualTo(Money.of("0.34"));
            assertThat(request.amount()).isEqualTo(Money.of("3.69"));
        }

        @Test
        void shouldShareTheServiceOfTheRequestByTheLargestRemainder() {
            TablePaymentRequest request = twoOfTrioWithSmall(true);

            assertThat(request.subtotal()).isEqualTo(Money.of("40.05"));
            assertThat(request.serviceCharge()).isEqualTo(Money.of("4.01"));
            assertThat(request.amount()).isEqualTo(Money.of("44.06"));
            assertThat(shareOf(request, trio)).isEqualTo(Money.of("0.67"));
            assertThat(shareOf(request, small)).isEqualTo(Money.of("3.34"));
        }

        @Test
        void shouldReserveOnlyTheUnitsChosen() {
            requestUnits(tab, true, choice(trio, 2));

            assertThat(trio.reservedQuantity()).isEqualTo(2);
            assertThat(trio.paidQuantity()).isZero();
            assertThat(trio.freeQuantity()).isEqualTo(1);
        }

        @Test
        void shouldNotCallALinePaidWhileOneUnitIsStillUnpaid() {
            twoOfTrioWithSmall(true).confirm(tab, billing, WAITER, CONFIRMED_AT);

            assertThat(trio.paidQuantity()).isEqualTo(2);
            assertThat(trio.reservedQuantity()).isZero();
            assertThat(trio.isPaid()).isFalse();
            assertThat(trio.paidServiceCharge()).isEqualTo(Money.of("0.67"));
            assertThat(small.isPaid()).isTrue();
        }

        @Test
        void shouldCallALinePaidOnceTheLastUnitIsPaid() {
            requestUnits(tab, true, choice(trio, 2)).confirm(tab, billing, WAITER, CONFIRMED_AT);

            requestUnits(tab, true, choice(trio, 1)).confirm(tab, billing, WAITER, CONFIRMED_AT);

            assertThat(trio.isPaid()).isTrue();
            assertThat(trio.paidServiceCharge()).isEqualTo(Money.of("1.01"));
        }

        /** Unpaid: 100.00 + 3.35 = 103.35, whose 10.335 of service rounds to 10.34 on its own. */
        @Test
        void shouldAddTheFrozenSharesToTheServiceOfTheUnpaidUnitsAndKeepTotalEqualToPaidPlusDue() {
            twoOfTrioWithSmall(true).confirm(tab, billing, WAITER, CONFIRMED_AT);

            TabBill bill = tab.bill(TEN_PERCENT);
            assertThat(bill.serviceCharge()).isEqualTo(Money.of("14.35"));
            assertThat(bill.total()).isEqualTo(Money.of("157.75"));
            assertThat(bill.paidAmount()).isEqualTo(Money.of("44.06"));
            assertThat(bill.amountDue()).isEqualTo(Money.of("113.69"));
            assertThat(bill.paidAmount().plus(bill.amountDue())).isEqualTo(bill.total());
        }

        @Test
        void shouldPostOnlyTheUnpaidUnitsWhenClosing() {
            twoOfTrioWithSmall(true).confirm(tab, billing, WAITER, CONFIRMED_AT);

            startClosing();

            assertThat(billing.postings.get(1).chargeId()).isEqualTo(tab.tabChargeId().orElseThrow());
            assertThat(billing.postings.get(1).amount()).isEqualTo(Money.of("113.69"));
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.of("113.69"));
        }

        /** Closed at 157.74; 2 units at the frozen 10% cost 7.37; unpaid 136.70 + 13.67 = 150.37. */
        @Test
        void shouldSwapTheClosingChargeForTheUnitsStillDueWhenUnitsArePaidWhileClosing() {
            startClosing();
            TablePaymentRequest request = TablePaymentRequest.request(PROPERTY_ID, tab, CUSTOMER_NAME, DEVICE_ID,
                    List.of(choice(trio, 2)), true, TWENTY_PERCENT, Optional.of(TablePaymentFixtures.RECEIVER),
                    REQUESTED_WHILE_CLOSING_AT);

            request.confirm(tab, billing, WAITER, CONFIRMED_WHILE_CLOSING_AT);

            assertThat(request.amount()).isEqualTo(Money.of("7.37"));
            assertThat(billing.liveChargesOn(folio())).isEqualTo(Money.of("157.74"));
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.of("150.37"));
            assertThat(tab.amountDue(TEN_PERCENT)).isEqualTo(Money.of("150.37"));
        }

        @Test
        void shouldAcceptExactlyTheFreeUnitsLeft() {
            requestUnits(tab, true, choice(trio, 2)).confirm(tab, billing, WAITER, CONFIRMED_AT);

            TablePaymentRequest last = requestUnits(tab, true, choice(trio, 1));

            assertThat(last.subtotal()).isEqualTo(Money.of("3.35"));
        }

        @Test
        void shouldAnswerAlreadyPaidForOneUnitMoreThanTheFreeOnesWhenTheRestIsPaid() {
            requestUnits(tab, true, choice(trio, 2)).confirm(tab, billing, WAITER, CONFIRMED_AT);

            assertRejectedWith(() -> requestUnits(tab, true, choice(trio, 2)), TAB_ITEM_ALREADY_PAID);
            assertThat(trio.reservedQuantity()).isZero();
        }

        @Test
        void shouldAnswerReservedForOneUnitMoreThanTheFreeOnesWhenAnotherRequestHoldsSome() {
            requestUnits(tab, true, choice(trio, 1)).confirm(tab, billing, WAITER, CONFIRMED_AT);
            requestUnits(tab, true, choice(trio, 1));

            assertRejectedWith(() -> requestUnits(tab, true, choice(trio, 2)), TAB_ITEM_RESERVED_FOR_PAYMENT);
            assertThat(trio.reservedQuantity()).isEqualTo(1);
        }

        @Test
        void shouldRefuseZeroUnits() {
            assertRejectedWith(() -> requestUnits(tab, true, choice(trio, 0)), INVALID_TABLE_PAYMENT_REQUEST);
        }

        @Test
        void shouldRefuseOneUnitMoreThanTheLine() {
            assertRejectedWith(() -> requestUnits(tab, true, choice(trio, 4)), INVALID_TABLE_PAYMENT_REQUEST);
        }

        @Test
        void shouldRefuseAMissingQuantity() {
            assertRejectedWith(() -> requestUnits(tab, true, choice(trio, null)), INVALID_TABLE_PAYMENT_REQUEST);
        }

        @Test
        void shouldRefuseTheSameItemChosenTwice() {
            assertRejectedWith(() -> requestUnits(tab, true, choice(trio, 1), choice(trio, 1)),
                    INVALID_TABLE_PAYMENT_REQUEST);
            assertThat(trio.reservedQuantity()).isZero();
        }

        @Test
        void shouldReserveNothingWhenOneQuantityFallsOutsideItsLine() {
            assertRejectedWith(() -> requestUnits(tab, true, whole(small), choice(trio, 4)),
                    INVALID_TABLE_PAYMENT_REQUEST);
            assertThat(small.isReservedForPayment()).isFalse();
        }
    }

    // ------------------------------------------------------------------ the service charge is optional (#38, #45)

    @Nested
    class ServiceChargeLeftOut {

        private final TabItem trio = order(tab, dish("3.35"), units(3));

        @Test
        void shouldChargeOnlyTheUnitsWithAZeroShareOnEachItem() {
            TablePaymentRequest request = requestUnits(tab, false, choice(trio, 2), whole(small));

            assertThat(request.includeServiceCharge()).isFalse();
            assertThat(request.serviceCharge()).isEqualTo(Money.ZERO);
            assertThat(request.amount()).isEqualTo(Money.of("40.05"));
            assertThat(request.items()).extracting(TablePaymentRequestItem::serviceCharge)
                    .containsOnly(Money.ZERO);
        }

        @Test
        void shouldFreezeNoServiceOnTheUnitsPaidWithoutIt() {
            requestUnits(tab, false, choice(trio, 2), whole(small)).confirm(tab, billing, WAITER, CONFIRMED_AT);

            assertThat(trio.paidServiceCharge()).isEqualTo(Money.ZERO);
            assertThat(small.paidServiceCharge()).isEqualTo(Money.ZERO);
        }

        /** Nothing frozen, and the unpaid 103.35 still owes 10.34: 143.40 + 10.34 = 153.74. */
        @Test
        void shouldStillChargeServiceOnTheUnpaidUnitsAndKeepTotalEqualToPaidPlusDue() {
            requestUnits(tab, false, choice(trio, 2), whole(small)).confirm(tab, billing, WAITER, CONFIRMED_AT);

            TabBill bill = tab.bill(TEN_PERCENT);
            assertThat(bill.serviceCharge()).isEqualTo(Money.of("10.34"));
            assertThat(bill.total()).isEqualTo(Money.of("153.74"));
            assertThat(bill.paidAmount()).isEqualTo(Money.of("40.05"));
            assertThat(bill.amountDue()).isEqualTo(Money.of("113.69"));
            assertThat(bill.paidAmount().plus(bill.amountDue())).isEqualTo(bill.total());
        }

        @Test
        void shouldPostTheServiceOfTheUnpaidUnitsWhenClosing() {
            requestUnits(tab, false, choice(trio, 2), whole(small)).confirm(tab, billing, WAITER, CONFIRMED_AT);

            startClosing();

            assertThat(billing.postings.get(1).amount()).isEqualTo(Money.of("113.69"));
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.of("113.69"));
        }
    }

    // ------------------------------------------------------------------ giving back exactly the request's units (#42)

    @Nested
    class GivingBackTheUnits {

        private final TabItem trio = order(tab, dish("3.35"), units(3));

        @Test
        void shouldFreeOnlyTheUnitsOfTheRejectedRequest() {
            TablePaymentRequest first = requestUnits(tab, true, choice(trio, 1));
            requestUnits(tab, true, choice(trio, 1));

            first.reject(tab, WAITER, CONFIRMED_AT);

            assertThat(trio.reservedQuantity()).isEqualTo(1);
            assertThat(trio.freeQuantity()).isEqualTo(2);
        }

        @Test
        void shouldFreeOnlyTheUnitsOfTheExpiredRequest() {
            TablePaymentRequest first = requestUnits(tab, true, choice(trio, 2));
            requestUnits(tab, true, choice(trio, 1));

            first.expire(tab, AFTER_WINDOW);

            assertThat(trio.reservedQuantity()).isEqualTo(1);
            assertThat(trio.freeQuantity()).isEqualTo(2);
        }

        @Test
        void shouldTakeBackOnlyTheUnitsAndTheShareOfTheRefundedRequest() {
            requestUnits(tab, false, choice(trio, 1)).confirm(tab, billing, WAITER, CONFIRMED_AT);
            TablePaymentRequest withService = requestUnits(tab, true, choice(trio, 1));
            withService.confirm(tab, billing, WAITER, CONFIRMED_AT);

            withService.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT);

            assertThat(trio.paidQuantity()).isEqualTo(1);
            assertThat(trio.paidServiceCharge()).isEqualTo(Money.ZERO);
            assertThat(trio.freeQuantity()).isEqualTo(2);
        }

        @Test
        void shouldKeepTheShareOfTheOtherPartWhenTheOneWithoutServiceIsRefunded() {
            TablePaymentRequest withoutService = requestUnits(tab, false, choice(trio, 1));
            withoutService.confirm(tab, billing, WAITER, CONFIRMED_AT);
            requestUnits(tab, true, choice(trio, 1)).confirm(tab, billing, WAITER, CONFIRMED_AT);

            withoutService.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT);

            assertThat(trio.paidQuantity()).isEqualTo(1);
            assertThat(trio.paidServiceCharge()).isEqualTo(Money.of("0.34"));
        }

        /** Left paid: 1 unit, 3.35 without service. Unpaid 140.05 owes 14.01: 143.40 + 14.01 = 157.41. */
        @Test
        void shouldKeepTotalEqualToPaidPlusDueAfterARefundOfPartOfTheUnits() {
            requestUnits(tab, false, choice(trio, 1)).confirm(tab, billing, WAITER, CONFIRMED_AT);
            TablePaymentRequest withService = requestUnits(tab, true, choice(trio, 1));
            withService.confirm(tab, billing, WAITER, CONFIRMED_AT);

            withService.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT);

            TabBill bill = tab.bill(TEN_PERCENT);
            assertThat(bill.paidAmount()).isEqualTo(Money.of("3.35"));
            assertThat(bill.total()).isEqualTo(Money.of("157.41"));
            assertThat(bill.amountDue()).isEqualTo(Money.of("154.06"));
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.ZERO);
        }
    }

    // ------------------------------------------------------------------ sold by weight is paid whole (#39, #44)

    @Nested
    class SoldByWeight {

        private final TabItem plate = order(tab, buffet(), grams(437));

        @Test
        void shouldPayAPlateWholeAsItsOnlyUnit() {
            requestUnits(tab, true, choice(plate, 1)).confirm(tab, billing, WAITER, CONFIRMED_AT);

            assertThat(plate.isPaid()).isTrue();
            assertThat(plate.paidQuantity()).isEqualTo(1);
        }

        @Test
        void shouldRefuseTwoUnitsOfAPlate() {
            assertRejectedWith(() -> requestUnits(tab, true, choice(plate, 2)), INVALID_TABLE_PAYMENT_REQUEST);
        }
    }

    // ------------------------------------------------------------------ split groups with frozen shares (#48, #54)

    @Nested
    class SplitGroupsWithPaidUnits {

        private static Money sumOfTotals(TabBill bill) {
            return bill.groups().stream().map(TabBill.SplitGroup::total).reduce(Money.ZERO, Money::plus);
        }

        private static Money sumOfServiceCharges(TabBill bill) {
            return bill.groups().stream().map(TabBill.SplitGroup::serviceCharge).reduce(Money.ZERO, Money::plus);
        }

        private TabBill.SplitGroup group(TabBill bill, int number) {
            return bill.group(number);
        }

        /** Pizza 40.00 paid with its 4.00, then the waiter takes the service off the tab: the 4.00 stays. */
        @Test
        void shouldKeepTheFrozenShareInItsGroupWhenTheServiceIsTakenOffTheTabAfterwards() {
            Tab table = tableTab();
            TabItem pizza = orderDish(table, "40.00");
            TabItem soda = orderDish(table, "6.00");
            table.assignToSplitGroup(Map.of(pizza.id(), 1, soda.id(), 2));
            requestAt(table, TEN_PERCENT, REQUESTED_AT, pizza).confirm(table, billing, WAITER, CONFIRMED_AT);

            table.removeServiceCharge();

            TabBill bill = table.bill(TEN_PERCENT);
            assertThat(group(bill, 1).serviceCharge()).isEqualTo(Money.of("4.00"));
            assertThat(group(bill, 2).serviceCharge()).isEqualTo(Money.ZERO);
            assertThat(sumOfServiceCharges(bill)).isEqualTo(bill.serviceCharge());
            assertThat(sumOfTotals(bill)).isEqualTo(bill.total());
            assertThat(bill.total()).isEqualTo(Money.of("50.00"));
            assertThat(bill.amountDue()).isEqualTo(Money.of("6.00"));
        }

        /**
         * Group 1: 3 × 3.35; group 2: 2 × 10.05 and 100.01. Paid without service: 2 of the 3.35 and 1 of
         * the 10.05. Unpaid 3.35 | 110.06 = 113.41, whose 11.341 rounds to 11.34, shared 0.335 | 11.006
         * floored to 0.33 | 11.00 with the cent to the larger remainder: 0.33 | 11.01.
         */
        @Test
        void shouldShareTheUnpaidServiceAmongGroupsByTheLargestRemainderOfTheirUnpaidBase() {
            Tab table = tableTab();
            TabItem trio = order(table, dish("3.35"), units(3));
            TabItem duo = order(table, dish("10.05"), units(2));
            TabItem big = orderDish(table, "100.01");
            table.assignToSplitGroup(Map.of(trio.id(), 1, duo.id(), 2, big.id(), 2));
            requestUnits(table, false, choice(trio, 2), choice(duo, 1)).confirm(table, billing, WAITER, CONFIRMED_AT);

            TabBill bill = table.bill(TEN_PERCENT);

            assertThat(bill.serviceCharge()).isEqualTo(Money.of("11.34"));
            assertThat(group(bill, 1).serviceCharge()).isEqualTo(Money.of("0.33"));
            assertThat(group(bill, 2).serviceCharge()).isEqualTo(Money.of("11.01"));
            assertThat(group(bill, 1).total()).isEqualTo(Money.of("10.38"));
            assertThat(group(bill, 2).total()).isEqualTo(Money.of("131.12"));
            assertThat(sumOfTotals(bill)).isEqualTo(bill.total());
            assertThat(bill.total()).isEqualTo(Money.of("141.50"));
        }

        /** The 2-of-3 example of {@link PartOfTheQuantity}: 4.01 frozen plus 103.35 × 10% = 10.34. */
        @Test
        void shouldShowAnUnpaidBaseThatTimesTheRatePlusTheFrozenSharesGivesTheServiceShown() {
            TabItem trio = order(tab, dish("3.35"), units(3));
            requestUnits(tab, true, choice(trio, 2), whole(small)).confirm(tab, billing, WAITER, CONFIRMED_AT);

            TabBill bill = tab.bill(TEN_PERCENT);

            assertThat(bill.unpaidServiceChargeBase()).isEqualTo(Money.of("103.35"));
            Money frozen = tab.items().stream().map(TabItem::paidServiceCharge).reduce(Money.ZERO, Money::plus);
            assertThat(bill.unpaidServiceChargeBase().percentage(TEN_PERCENT).plus(frozen))
                    .isEqualTo(bill.serviceCharge());
        }
    }

    // ------------------------------------------------------------------ Pix already refunded at the counter (#49)

    @Nested
    class RefundedAtTheCounterFirst {

        @Test
        void shouldReverseThePartAndFreeTheUnitsWithoutRefundingThePixTwice() {
            TablePaymentRequest request = paySmall();
            billing.refundPayment(folio(), request.paymentId().orElseThrow(), "Estornado no balcao");

            request.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT);

            assertThat(billing.refunds).hasSize(1);
            assertThat(billing.reversals).extracting(FakeTabBilling.Reversal::chargeId)
                    .containsExactly(request.partChargeId().orElseThrow());
            assertThat(small.paidQuantity()).isZero();
            assertThat(small.freeQuantity()).isEqualTo(1);
            assertThat(request.isRefunded()).isTrue();
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.ZERO);
        }
    }

    // ------------------------------------------------------------------ the quote (#55, #57)

    @Nested
    class Quote {

        private final TabItem trio = order(tab, dish("3.35"), units(3));

        private List<Tab.PartChoice> twoOfTrioWithSmall() {
            return List.of(choice(trio, 2), whole(small));
        }

        private static List<String> linesOf(List<TablePaymentRequestItem> items) {
            return items.stream().map(item -> item.tabItemId() + " " + item.quantity() + " " + item.lineTotal()
                    + " " + item.serviceCharge()).toList();
        }

        @Test
        void shouldPriceTheUnitsExactlyAsTheRequestCreatedRightAfterReservesThem() {
            Tab.PaymentPart priced = tab.priceForPayment(twoOfTrioWithSmall(), true, TEN_PERCENT);

            TablePaymentRequest request = requestUnits(tab, true, choice(trio, 2), whole(small));

            assertThat(linesOf(priced.items())).containsExactlyElementsOf(linesOf(request.items()));
            assertThat(priced.serviceChargeRate()).isEqualTo(request.serviceChargeRate());
        }

        @Test
        void shouldReserveNothingWhenPricing() {
            tab.priceForPayment(twoOfTrioWithSmall(), true, TEN_PERCENT);

            assertThat(trio.reservedQuantity()).isZero();
            assertThat(small.isReservedForPayment()).isFalse();
        }

        @Test
        void shouldGiveZeroSharesWhenThePriceLeavesTheServiceOut() {
            Tab.PaymentPart priced = tab.priceForPayment(twoOfTrioWithSmall(), false, TEN_PERCENT);

            assertThat(priced.items()).extracting(TablePaymentRequestItem::serviceCharge).containsOnly(Money.ZERO);
        }

        @Test
        void shouldRefuseAQuoteWithoutAPixReceiverBeforeLookingAtTheItems() {
            assertRejectedWith(() -> TablePaymentRequest.quote(tab, List.of(choice(trio, 4)), true, TEN_PERCENT,
                    Optional.empty()), PIX_RECEIVER_NOT_CONFIGURED);
        }

        @Test
        void shouldRefuseAQuoteOfUnitsAlreadyPaid() {
            paySmall();

            assertRejectedWith(() -> TablePaymentRequest.quote(tab, List.of(whole(small)), true, TEN_PERCENT,
                    Optional.of(TablePaymentFixtures.RECEIVER)), TAB_ITEM_ALREADY_PAID);
        }

        @Test
        void shouldWriteNothingWhenQuoting() {
            Tab.PaymentPart quoted = TablePaymentRequest.quote(tab, twoOfTrioWithSmall(), true, TEN_PERCENT,
                    Optional.of(TablePaymentFixtures.RECEIVER));

            assertThat(quoted.items()).hasSize(2);
            assertThat(trio.reservedQuantity()).isZero();
            assertThat(small.isReservedForPayment()).isFalse();
            assertThat(billing.postings).isEmpty();
            assertThat(billing.openedFolios).isEmpty();
        }
    }

    private static Money shareOf(TablePaymentRequest request, TabItem item) {
        return request.items().stream()
                .filter(requestItem -> requestItem.tabItemId().equals(item.id()))
                .findFirst()
                .orElseThrow()
                .serviceCharge();
    }

    // ------------------------------------------------------------------ what can be chosen

    @Nested
    class Choosing {

        @Test
        void shouldRefuseAnItemAnotherPaymentHolds() {
            request(tab, small);

            assertRejectedWith(() -> request(tab, small), TAB_ITEM_RESERVED_FOR_PAYMENT);
        }

        @Test
        void shouldRefuseAnItemAlreadyPaid() {
            paySmall();

            assertRejectedWith(() -> request(tab, small), TAB_ITEM_ALREADY_PAID);
        }

        @Test
        void shouldReserveNothingWhenOneOfTheItemsIsHeld() {
            request(tab, small);

            assertRejectedWith(() -> request(tab, hundred, small), TAB_ITEM_RESERVED_FOR_PAYMENT);
            assertThat(hundred.isReservedForPayment()).isFalse();
        }

        @Test
        void shouldLetTwoPeopleOfTheTablePayDifferentItemsAtOnce() {
            request(tab, small);

            TablePaymentRequest second = request(tab, hundred);

            assertThat(second.status()).isEqualTo(TablePaymentRequestStatus.PENDING);
            assertThat(hundred.isReservedForPayment()).isTrue();
            assertThat(small.isReservedForPayment()).isTrue();
        }

        @Test
        void shouldRefuseARequestWithoutItems() {
            assertRejectedWith(() -> request(tab), INVALID_TABLE_PAYMENT_REQUEST);
        }

        @Test
        void shouldRefuseACancelledItem() {
            tab.cancelItem(small.id(), "Pedido errado", WAITER, REQUESTED_AT);

            assertRejectedWith(() -> request(tab, small), INVALID_TABLE_PAYMENT_REQUEST);
        }

        @Test
        void shouldRefuseAnItemOfAnotherTab() {
            Tab other = tableTab();
            TabItem foreign = orderDish(other, "20.00");

            assertRejectedWith(() -> request(tab, foreign), INVALID_TABLE_PAYMENT_REQUEST);
        }

        @Test
        void shouldRefuseWhenNoPixReceiverIsConfigured() {
            assertRejectedWith(
                    () -> TablePaymentRequest.request(PROPERTY_ID, tab, CUSTOMER_NAME, DEVICE_ID, List.of(whole(small)),
                            true, TEN_PERCENT, Optional.empty(), REQUESTED_AT),
                    PIX_RECEIVER_NOT_CONFIGURED);
            assertThat(small.isReservedForPayment()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = TabStatus.class, names = {"CLOSED", "CANCELLED", "MERGED"})
        void shouldRefuseAPaymentOnATabNeitherOpenNorClosing(TabStatus status) {
            Tab finished = tabInStatus(status, new FakeTabBilling());

            assertRejectedWith(
                    () -> TablePaymentRequest.request(PROPERTY_ID, finished, CUSTOMER_NAME, DEVICE_ID,
                            List.of(new Tab.PartChoice(TabItemId.newId(), 1)), true, TEN_PERCENT,
                            Optional.of(TablePaymentFixtures.RECEIVER), REQUESTED_AT),
                    TAB_NOT_OPEN);
        }
    }

    // ------------------------------------------------------------------ a held item is locked

    enum Hold {
        RESERVED(TAB_ITEM_RESERVED_FOR_PAYMENT),
        PAID(TAB_ITEM_ALREADY_PAID);

        final String code;

        Hold(String code) {
            this.code = code;
        }
    }

    /** Holds one unit only: the whole line is refused when any unit is held (decision #43). */
    private void holdOneUnit(Tab owner, TabItem item, Hold hold) {
        TablePaymentRequest request = TablePaymentRequest.request(PROPERTY_ID, owner, CUSTOMER_NAME, DEVICE_ID,
                List.of(choice(item, 1)), true, TEN_PERCENT, Optional.of(TablePaymentFixtures.RECEIVER),
                REQUESTED_AT);
        if (hold == Hold.PAID) {
            request.confirm(owner, billing, WAITER, CONFIRMED_AT);
        }
    }

    @Nested
    class HeldItemIsLocked {

        private final TabItem trio = order(tab, dish("3.35"), units(3));

        @ParameterizedTest
        @EnumSource(Hold.class)
        void shouldRefuseToCancelALineWithOneUnitHeld(Hold hold) {
            holdOneUnit(tab, trio, hold);

            assertRejectedWith(() -> tab.cancelItem(trio.id(), "Cliente desistiu", WAITER, CONFIRMED_AT), hold.code);
            assertThat(trio.isActive()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(Hold.class)
        void shouldRefuseToTransferALineWithOneUnitHeld(Hold hold) {
            holdOneUnit(tab, trio, hold);
            Tab destination = tableTab();

            assertRejectedWith(
                    () -> tab.transferItemsTo(destination, Set.of(trio.id()), WAITER, TRANSFERRED_AT), hold.code);
            assertThat(destination.items()).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(Hold.class)
        void shouldRefuseToMergeATabWithOneUnitHeld(Hold hold) {
            holdOneUnit(tab, trio, hold);
            Tab target = tableTab();

            assertRejectedWith(() -> target.mergeWith(tab, billing, WAITER, MERGED_AT), hold.code);
            assertThat(tab.status()).isEqualTo(TabStatus.OPEN);
        }

        @ParameterizedTest
        @EnumSource(Hold.class)
        void shouldRefuseToMoveATabWithOneUnitHeldToAnotherTable(Hold hold) {
            holdOneUnit(tab, trio, hold);
            DiningTable destination = activeTable();
            Optional<DiningTableId> before = tab.diningTableId();

            assertRejectedWith(() -> tab.moveToTable(destination, billing, WAITER, TRANSFERRED_AT), hold.code);
            assertThat(tab.diningTableId()).isEqualTo(before);
        }

        @Test
        void shouldRefuseToCancelAWholeLinePaid() {
            paySmall();

            assertRejectedWith(() -> tab.cancelItem(small.id(), "Cliente desistiu", WAITER, CONFIRMED_AT),
                    TAB_ITEM_ALREADY_PAID);
        }

        @Test
        void shouldLetTheFreeItemsOfTheTabBeTransferred() {
            holdOneUnit(tab, trio, Hold.RESERVED);
            Tab destination = tableTab();

            tab.transferItemsTo(destination, Set.of(hundred.id()), WAITER, TRANSFERRED_AT);

            assertThat(destination.items()).hasSize(1);
        }

        @Test
        void shouldLetTheFreeUnitsOfAPartlyPaidLineStillBePaid() {
            holdOneUnit(tab, trio, Hold.PAID);

            TablePaymentRequest rest = requestUnits(tab, true, choice(trio, 2));

            assertThat(rest.subtotal()).isEqualTo(Money.of("6.70"));
            assertThat(trio.freeQuantity()).isZero();
        }
    }

    // ------------------------------------------------------------------ confirming while OPEN

    @Nested
    class ConfirmingWhileOpen {

        @Test
        void shouldOpenTheFolioPostThePartAndRegisterThePixOfTheSameAmount() {
            TablePaymentRequest request = paySmall();

            assertThat(billing.openedFolios).hasSize(1);
            assertThat(billing.postings).singleElement().satisfies(posting -> {
                assertThat(posting.amount()).isEqualTo(Money.of("36.69"));
                assertThat(posting.tabId()).isEqualTo(tab.id());
                assertThat(posting.chargeId()).isEqualTo(request.partChargeId().orElseThrow());
            });
            assertThat(billing.payments).singleElement().satisfies(payment -> {
                assertThat(payment.method()).isEqualTo(PaymentMethod.PIX);
                assertThat(payment.amount()).isEqualTo(Money.of("36.69"));
                assertThat(payment.paymentId()).isEqualTo(request.paymentId().orElseThrow());
            });
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldKeyThePixByTheIdOfTheRequest() {
            TablePaymentRequest request = paySmall();

            assertThat(billing.idempotencyKeys).containsExactly(request.id().value().toString());
        }

        @Test
        void shouldReuseTheFolioOfTheTabForTheSecondPart() {
            paySmall();
            TablePaymentRequest second = request(tab, hundred);

            second.confirm(tab, billing, WAITER, CONFIRMED_AT);

            assertThat(billing.openedFolios).hasSize(1);
            assertThat(billing.postings).hasSize(2);
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldPostTheFrozenAmountEvenIfTheRateChangedBeforeConfirmation() {
            TablePaymentRequest request = requestAt(tab, TWENTY_PERCENT, REQUESTED_AT, small);

            request.confirm(tab, billing, WAITER, CONFIRMED_AT);

            assertThat(billing.postings.get(0).amount()).isEqualTo(Money.of("40.02"));
            assertThat(tab.paidAmount()).isEqualTo(Money.of("40.02"));
        }
    }

    // ------------------------------------------------------------------ closing with parts paid

    @Nested
    class ClosingWithPartsPaid {

        @Test
        void shouldPostOnlyWhatIsStillDue() {
            paySmall();

            startClosing();

            assertThat(billing.postings).hasSize(2);
            ChargeId closingCharge = tab.tabChargeId().orElseThrow();
            assertThat(billing.postings.get(1).chargeId()).isEqualTo(closingCharge);
            assertThat(billing.postings.get(1).amount()).isEqualTo(Money.of("110.00"));
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.of("110.00"));
        }

        @Test
        void shouldPostNothingWhenEverythingWasPaid() {
            TablePaymentRequest all = request(tab, hundred, small);
            all.confirm(tab, billing, WAITER, CONFIRMED_AT);

            startClosing();

            assertThat(tab.status()).isEqualTo(TabStatus.CLOSING);
            assertThat(billing.postings).hasSize(1);
            assertThat(tab.tabChargeId()).isEmpty();
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldCloseATabPaidEntirelyFromThePhone() {
            request(tab, hundred, small).confirm(tab, billing, WAITER, CONFIRMED_AT);
            startClosing();

            tab.close(billing, WAITER, CLOSED_AT);

            assertThat(tab.status()).isEqualTo(TabStatus.CLOSED);
            assertThat(billing.closedFolios).containsExactly(folio());
        }

        @Test
        void shouldReverseOnlyTheClosingChargeOnReopen() {
            TablePaymentRequest part = paySmall();
            startClosing();
            ChargeId closingCharge = tab.tabChargeId().orElseThrow();

            tab.reopen("Pediram sobremesa", billing);

            assertThat(billing.reversals).extracting(FakeTabBilling.Reversal::chargeId).containsExactly(closingCharge);
            assertThat(billing.reversals).extracting(FakeTabBilling.Reversal::chargeId)
                    .doesNotContain(part.partChargeId().orElseThrow());
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.ZERO);
            assertThat(small.isPaid()).isTrue();
        }

        @Test
        void shouldReverseNothingOnReopenWhenTheClosingPostedNothing() {
            request(tab, hundred, small).confirm(tab, billing, WAITER, CONFIRMED_AT);
            startClosing();

            tab.reopen("Pediram sobremesa", billing);

            assertThat(tab.status()).isEqualTo(TabStatus.OPEN);
            assertThat(billing.reversals).isEmpty();
        }

        @Test
        void shouldRefuseToCloseWhileAPaymentStillWaitsForTheWaiter() {
            startClosing();
            requestAt(tab, TEN_PERCENT, REQUESTED_WHILE_CLOSING_AT, small);
            tab.receivePayment(PaymentMethod.CASH, Money.of("146.69"), "cash-all", billing);

            assertRejectedWith(() -> tab.close(billing, WAITER, CLOSED_AT), TAB_ITEM_RESERVED_FOR_PAYMENT);
            assertThat(tab.status()).isEqualTo(TabStatus.CLOSING);
        }
    }

    // ------------------------------------------------------------------ confirming while CLOSING (#25)

    @Nested
    class ConfirmingWhileClosing {

        private TablePaymentRequest closeThenPaySmall() {
            startClosing();
            TablePaymentRequest request = requestAt(tab, TEN_PERCENT, REQUESTED_WHILE_CLOSING_AT, small);
            request.confirm(tab, billing, WAITER, CONFIRMED_WHILE_CLOSING_AT);
            return request;
        }

        @Test
        void shouldSwapTheClosingChargeForWhatIsStillDue() {
            startClosing();
            ChargeId firstClosingCharge = tab.tabChargeId().orElseThrow();
            TablePaymentRequest request = requestAt(tab, TEN_PERCENT, REQUESTED_WHILE_CLOSING_AT, small);

            request.confirm(tab, billing, WAITER, CONFIRMED_WHILE_CLOSING_AT);

            assertThat(billing.reversals).extracting(FakeTabBilling.Reversal::chargeId)
                    .containsExactly(firstClosingCharge);
            ChargeId newClosingCharge = tab.tabChargeId().orElseThrow();
            assertThat(newClosingCharge).isNotEqualTo(firstClosingCharge);
            assertThat(billing.postings).filteredOn(posting -> posting.chargeId().equals(newClosingCharge))
                    .singleElement().extracting(FakeTabBilling.Posting::amount).isEqualTo(Money.of("110.00"));
            assertThat(billing.postings).filteredOn(posting -> posting.chargeId().equals(
                            request.partChargeId().orElseThrow()))
                    .singleElement().extracting(FakeTabBilling.Posting::amount).isEqualTo(Money.of("36.69"));
        }

        @Test
        void shouldKeepTheLiveChargesEqualToTheTotalAndTheBalanceEqualToWhatIsDue() {
            closeThenPaySmall();

            assertThat(billing.liveChargesOn(folio())).isEqualTo(Money.of("146.69"));
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.of("110.00"));
            assertThat(tab.amountDue(TEN_PERCENT)).isEqualTo(Money.of("110.00"));
        }

        @Test
        void shouldLeaveTheBalanceAtZeroWhenReopenedAfterAPartPaidWhileClosing() {
            closeThenPaySmall();

            tab.reopen("Pediram sobremesa", billing);

            assertThat(billing.liveChargesOn(folio())).isEqualTo(Money.of("36.69"));
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldPostNoNewClosingChargeWhenThePartPaysEverythingLeft() {
            request(tab, hundred).confirm(tab, billing, WAITER, CONFIRMED_AT);
            startClosing();
            TablePaymentRequest last = requestAt(tab, TEN_PERCENT, REQUESTED_WHILE_CLOSING_AT, small);

            last.confirm(tab, billing, WAITER, CONFIRMED_WHILE_CLOSING_AT);

            assertThat(tab.tabChargeId()).isEmpty();
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.ZERO);
            tab.close(billing, WAITER, CLOSED_AT);
            assertThat(tab.status()).isEqualTo(TabStatus.CLOSED);
        }
    }

    // ------------------------------------------------------------------ refund (#21, #30)

    @Nested
    class Refund {

        @Test
        void shouldRefundThePixReverseThePartAndFreeTheItems() {
            TablePaymentRequest request = paySmall();

            request.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT);

            assertThat(billing.refunds).singleElement().satisfies(refund -> {
                assertThat(refund.paymentId()).isEqualTo(request.paymentId().orElseThrow());
                assertThat(refund.reason()).isEqualTo(REFUND_REASON);
            });
            assertThat(billing.reversals).singleElement().satisfies(reversal -> {
                assertThat(reversal.chargeId()).isEqualTo(request.partChargeId().orElseThrow());
                assertThat(reversal.reason()).isEqualTo(REFUND_REASON);
            });
            assertThat(small.isPaid()).isFalse();
            assertThat(small.isReservedForPayment()).isFalse();
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.ZERO);
        }

        @Test
        void shouldStayConfirmedRecordingTheRefund() {
            TablePaymentRequest request = paySmall();

            request.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT);

            assertThat(request.status()).isEqualTo(TablePaymentRequestStatus.CONFIRMED);
            assertThat(request.isRefunded()).isTrue();
            assertThat(request.refundedAt()).contains(REFUNDED_AT);
            assertThat(request.refundedBy()).contains(WAITER);
            assertThat(request.refundReason()).contains(REFUND_REASON);
        }

        @Test
        void shouldPutTheRefundedPartBackOnWhatIsDue() {
            TablePaymentRequest request = paySmall();

            request.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT);

            assertThat(tab.paidAmount()).isEqualTo(Money.ZERO);
            assertThat(tab.amountDue(TEN_PERCENT)).isEqualTo(Money.of("146.69"));
        }

        @Test
        void shouldLetTheWaiterCancelAnItemOnceItsPaymentIsRefunded() {
            TablePaymentRequest request = paySmall();
            request.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT);

            tab.cancelItem(small.id(), "Prato voltou", WAITER, REFUNDED_AT);

            assertThat(small.isActive()).isFalse();
        }

        @Test
        void shouldLetARefundedItemBePaidAgain() {
            paySmall().refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT);

            TablePaymentRequest again = request(tab, small);

            assertThat(again.amount()).isEqualTo(Money.of("36.69"));
        }

        @Test
        void shouldRefuseASecondRefund() {
            TablePaymentRequest request = paySmall();
            request.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT);

            assertRejectedWith(() -> request.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT), NOT_REFUNDABLE);
            assertThat(billing.refunds).hasSize(1);
        }

        @Test
        void shouldRefuseToRefundAPendingRequest() {
            TablePaymentRequest request = request(tab, small);

            assertRejectedWith(() -> request.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT), NOT_REFUNDABLE);
            assertThat(small.isReservedForPayment()).isTrue();
        }

        @Test
        void shouldRefuseToRefundARejectedRequest() {
            TablePaymentRequest request = request(tab, small);
            request.reject(tab, WAITER, CONFIRMED_AT);

            assertRejectedWith(() -> request.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT), NOT_REFUNDABLE);
        }

        @Test
        void shouldSwapTheClosingChargeBackWhenRefundedWhileClosing() {
            TablePaymentRequest request = paySmall();
            startClosing();
            ChargeId closingCharge = tab.tabChargeId().orElseThrow();

            request.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT);

            assertThat(billing.reversals).extracting(FakeTabBilling.Reversal::chargeId)
                    .containsExactlyInAnyOrder(closingCharge, request.partChargeId().orElseThrow());
            assertThat(billing.liveChargesOn(folio())).isEqualTo(Money.of("146.69"));
            assertThat(billing.balanceOf(folio())).isEqualTo(Money.of("146.69"));
            assertThat(tab.tabChargeId()).isPresent().isNotEqualTo(Optional.of(closingCharge));
        }

        @Test
        void shouldRefuseToRefundOnAClosedTab() {
            TablePaymentRequest request = request(tab, hundred, small);
            request.confirm(tab, billing, WAITER, CONFIRMED_AT);
            startClosing();
            tab.close(billing, WAITER, CLOSED_AT);

            assertRejectedWith(() -> request.refund(tab, REFUND_REASON, billing, WAITER, REFUNDED_AT), TAB_NOT_OPEN);
            assertThat(billing.refunds).isEmpty();
        }
    }
}
