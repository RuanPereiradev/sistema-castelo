package br.com.castel.restaurant.domain;

import static br.com.castel.restaurant.domain.TabFixtures.OTHER_WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.WAITER;
import static br.com.castel.restaurant.domain.TabFixtures.assertRejectedWith;
import static br.com.castel.restaurant.domain.TabFixtures.orderDish;
import static br.com.castel.restaurant.domain.TabFixtures.tableTab;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.AFTER_WINDOW;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.CONFIRMED_AT;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.CUSTOMER_NAME;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.DEVICE_ID;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.EXPIRED;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.INVALID_CUSTOMER_DEVICE_ID;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.INVALID_CUSTOMER_NAME;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.NOT_PENDING;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.REQUESTED_AT;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.WINDOW_END;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.request;
import static br.com.castel.restaurant.domain.TablePaymentFixtures.requestBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.sharedkernel.Money;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The life of a payment sent from the phone of the table (decisions #8, #13 and #34 of task
 * qr-table-payment): born {@code PENDING} holding its items, answered once, expired after 30 minutes.
 */
class TablePaymentRequestTest {

    private final FakeTabBilling billing = new FakeTabBilling();
    private final Tab tab = tableTab();
    private final TabItem item = orderDish(tab, "33.35");

    private TablePaymentRequest pending() {
        return request(tab, item);
    }

    private TablePaymentRequest confirmed() {
        TablePaymentRequest request = pending();
        request.confirm(tab, billing, WAITER, CONFIRMED_AT);
        return request;
    }

    private TablePaymentRequest rejected() {
        TablePaymentRequest request = pending();
        request.reject(tab, WAITER, CONFIRMED_AT);
        return request;
    }

    private TablePaymentRequest expired() {
        TablePaymentRequest request = pending();
        request.expire(tab, AFTER_WINDOW);
        return request;
    }

    // ------------------------------------------------------------------ creation

    @Test
    void shouldBeBornPendingOnTheTableAndTabWithAThirtyMinuteWindowHoldingItsItems() {
        TablePaymentRequest request = pending();

        assertThat(request.status()).isEqualTo(TablePaymentRequestStatus.PENDING);
        assertThat(request.tabId()).isEqualTo(tab.id());
        assertThat(request.diningTableId()).isEqualTo(tab.diningTableId().orElseThrow());
        assertThat(request.expiresAt()).isEqualTo(WINDOW_END);
        assertThat(request.items()).extracting(TablePaymentRequestItem::tabItemId).containsExactly(item.id());
        assertThat(item.reservedQuantity()).isEqualTo(1);
        assertThat(item.freeQuantity()).isZero();
    }

    @Test
    void shouldRecordTheCustomerAndTheChoiceOfTheServiceCharge() {
        TablePaymentRequest request = pending();

        assertThat(request.customerName()).isEqualTo(CUSTOMER_NAME);
        assertThat(request.customerDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(request.includeServiceCharge()).isTrue();
    }

    // ------------------------------------------------------------------ the customer (#46)

    @Nested
    class Customer {

        @Test
        void shouldTrimTheNameOfTheCustomer() {
            TablePaymentRequest request = requestBy(tab, "  Ana  ", DEVICE_ID, item);

            assertThat(request.customerName()).isEqualTo("Ana");
        }

        @Test
        void shouldAcceptANameOfFortyCharacters() {
            TablePaymentRequest request = requestBy(tab, "a".repeat(40), DEVICE_ID, item);

            assertThat(request.customerName()).hasSize(40);
        }

        @Test
        void shouldRefuseANameOfFortyOneCharactersReservingNothing() {
            assertRejectedWith(() -> requestBy(tab, "a".repeat(41), DEVICE_ID, item), INVALID_CUSTOMER_NAME);
            assertThat(item.isReservedForPayment()).isFalse();
        }

        @Test
        void shouldRefuseABlankName() {
            assertRejectedWith(() -> requestBy(tab, "   ", DEVICE_ID, item), INVALID_CUSTOMER_NAME);
        }

        @Test
        void shouldRefuseAMissingName() {
            assertRejectedWith(() -> requestBy(tab, null, DEVICE_ID, item), INVALID_CUSTOMER_NAME);
        }

        @Test
        void shouldAcceptADeviceCodeOfEightCharacters() {
            TablePaymentRequest request = requestBy(tab, CUSTOMER_NAME, "abcd-_12", item);

            assertThat(request.customerDeviceId()).isEqualTo("abcd-_12");
        }

        @Test
        void shouldRefuseADeviceCodeOfSevenCharactersReservingNothing() {
            assertRejectedWith(() -> requestBy(tab, CUSTOMER_NAME, "abcd-12", item), INVALID_CUSTOMER_DEVICE_ID);
            assertThat(item.isReservedForPayment()).isFalse();
        }

        @Test
        void shouldAcceptADeviceCodeOfSixtyFourCharacters() {
            TablePaymentRequest request = requestBy(tab, CUSTOMER_NAME, "A".repeat(64), item);

            assertThat(request.customerDeviceId()).hasSize(64);
        }

        @Test
        void shouldRefuseADeviceCodeOfSixtyFiveCharacters() {
            assertRejectedWith(() -> requestBy(tab, CUSTOMER_NAME, "A".repeat(65), item), INVALID_CUSTOMER_DEVICE_ID);
        }

        @Test
        void shouldRefuseADeviceCodeWithACharacterOutsideLettersDigitsHyphenAndUnderscore() {
            assertRejectedWith(() -> requestBy(tab, CUSTOMER_NAME, "abcd.efgh", item), INVALID_CUSTOMER_DEVICE_ID);
        }

        @Test
        void shouldRefuseAMissingDeviceCode() {
            assertRejectedWith(() -> requestBy(tab, CUSTOMER_NAME, null, item), INVALID_CUSTOMER_DEVICE_ID);
        }
    }

    // ------------------------------------------------------------------ answering inside the window

    @Nested
    class AnsweringPending {

        @Test
        void shouldConfirmRecordingTheWaiterTheMomentThePixAndThePart() {
            TablePaymentRequest request = confirmed();

            assertThat(request.status()).isEqualTo(TablePaymentRequestStatus.CONFIRMED);
            assertThat(request.resolvedBy()).contains(WAITER);
            assertThat(request.resolvedAt()).contains(CONFIRMED_AT);
            assertThat(request.paymentId()).isPresent();
            assertThat(request.partChargeId()).isPresent();
            assertThat(request.isRefunded()).isFalse();
        }

        @Test
        void shouldRejectFreeingTheItemsWithoutTouchingTheFolio() {
            TablePaymentRequest request = rejected();

            assertThat(request.status()).isEqualTo(TablePaymentRequestStatus.REJECTED);
            assertThat(request.resolvedBy()).contains(WAITER);
            assertThat(item.isReservedForPayment()).isFalse();
            assertThat(item.isPaid()).isFalse();
            assertThat(billing.postings).isEmpty();
            assertThat(billing.payments).isEmpty();
        }

        @Test
        void shouldExpireAfterTheWindowFreeingTheItemsWithNoAuthor() {
            TablePaymentRequest request = expired();

            assertThat(request.status()).isEqualTo(TablePaymentRequestStatus.EXPIRED);
            assertThat(request.resolvedAt()).contains(AFTER_WINDOW);
            assertThat(request.resolvedBy()).isEmpty();
            assertThat(item.isReservedForPayment()).isFalse();
        }

        @Test
        void shouldRefuseToExpireARequestStillInsideItsWindow() {
            TablePaymentRequest request = pending();

            assertThatThrownBy(() -> request.expire(tab, WINDOW_END.minusNanos(1)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(item.isReservedForPayment()).isTrue();
        }

        @Test
        void shouldKeepTheItemsOfARejectedRequestOnItForTheRecord() {
            TablePaymentRequest request = rejected();

            assertThat(request.items()).extracting(TablePaymentRequestItem::tabItemId).containsExactly(item.id());
            assertThat(request.amount()).isEqualTo(Money.of("36.69"));
        }
    }

    // ------------------------------------------------------------------ a resolved request is final

    @Nested
    class ResolvedIsFinal {

        @Test
        void shouldRefuseConfirmationWhenRequestIsConfirmed() {
            TablePaymentRequest request = confirmed();

            assertRejectedWith(() -> request.confirm(tab, billing, OTHER_WAITER, CONFIRMED_AT), NOT_PENDING);
            assertThat(billing.payments).hasSize(1);
        }

        @Test
        void shouldRefuseRejectionWhenRequestIsConfirmed() {
            TablePaymentRequest request = confirmed();

            assertRejectedWith(() -> request.reject(tab, OTHER_WAITER, CONFIRMED_AT), NOT_PENDING);
            assertThat(item.isPaid()).isTrue();
        }

        @Test
        void shouldRefuseConfirmationWhenRequestIsRejected() {
            TablePaymentRequest request = rejected();

            assertRejectedWith(() -> request.confirm(tab, billing, OTHER_WAITER, CONFIRMED_AT), NOT_PENDING);
            assertThat(billing.payments).isEmpty();
        }

        @Test
        void shouldRefuseRejectionWhenRequestIsRejected() {
            TablePaymentRequest request = rejected();

            assertRejectedWith(() -> request.reject(tab, OTHER_WAITER, CONFIRMED_AT), NOT_PENDING);
        }

        @Test
        void shouldRefuseExpiryWhenRequestIsConfirmed() {
            TablePaymentRequest request = confirmed();

            assertRejectedWith(() -> request.expire(tab, AFTER_WINDOW), NOT_PENDING);
            assertThat(item.isPaid()).isTrue();
        }

        @Test
        void shouldRefuseExpiryWhenRequestIsAlreadyExpired() {
            TablePaymentRequest request = expired();

            assertRejectedWith(() -> request.expire(tab, AFTER_WINDOW), NOT_PENDING);
        }

        /** Same rule as the order request of the QR (decision #34): the job stored the expiry first. */
        @Test
        void shouldReportExpiredWhenConfirmingARequestTheJobAlreadyExpired() {
            TablePaymentRequest request = expired();

            assertRejectedWith(() -> request.confirm(tab, billing, WAITER, AFTER_WINDOW), EXPIRED);
            assertThat(request.status()).isEqualTo(TablePaymentRequestStatus.EXPIRED);
            assertThat(billing.payments).isEmpty();
            assertThat(billing.postings).isEmpty();
        }

        @Test
        void shouldReportExpiredWhenRejectingARequestTheJobAlreadyExpired() {
            TablePaymentRequest request = expired();

            assertRejectedWith(() -> request.reject(tab, WAITER, AFTER_WINDOW), EXPIRED);
            assertThat(request.status()).isEqualTo(TablePaymentRequestStatus.EXPIRED);
            assertThat(request.resolvedBy()).isEmpty();
        }

        @Test
        void shouldTellTheUseCaseAStoredExpiredRequestHasExpired() {
            TablePaymentRequest request = expired();

            assertRejectedWith(() -> request.requireAnswerableAt(AFTER_WINDOW), EXPIRED);
        }

        /**
         * A second waiter confirming a paid request after its window must learn that it was paid, not
         * that it expired: same answer as the order request of the QR (#23 of qr-table-ordering).
         */
        @Test
        void shouldReportNotPendingRatherThanExpiredWhenAConfirmedRequestIsConfirmedAgainLate() {
            TablePaymentRequest request = confirmed();

            assertRejectedWith(() -> request.confirm(tab, billing, OTHER_WAITER, AFTER_WINDOW), NOT_PENDING);
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
        void shouldReadAsExpiredOnceTheWindowIsOverWhileStillStoredPending() {
            TablePaymentRequest request = pending();

            assertThat(request.statusAt(WINDOW_END.minusNanos(1))).isEqualTo(TablePaymentRequestStatus.PENDING);
            assertThat(request.statusAt(WINDOW_END)).isEqualTo(TablePaymentRequestStatus.EXPIRED);
            assertThat(request.status()).isEqualTo(TablePaymentRequestStatus.PENDING);
        }

        @Test
        void shouldKeepReadingAConfirmedRequestAsConfirmedAfterTheWindow() {
            assertThat(confirmed().statusAt(AFTER_WINDOW)).isEqualTo(TablePaymentRequestStatus.CONFIRMED);
        }

        @Test
        void shouldConfirmOneNanosecondBeforeTheEndOfTheWindow() {
            TablePaymentRequest request = pending();

            request.confirm(tab, billing, WAITER, WINDOW_END.minusNanos(1));

            assertThat(request.status()).isEqualTo(TablePaymentRequestStatus.CONFIRMED);
        }

        @Test
        void shouldRefuseConfirmationOfAnOverdueRequestPostingNothing() {
            TablePaymentRequest request = pending();

            assertRejectedWith(() -> request.confirm(tab, billing, WAITER, WINDOW_END), EXPIRED);
            assertThat(billing.postings).isEmpty();
            assertThat(billing.payments).isEmpty();
            assertThat(item.isPaid()).isFalse();
        }

        @Test
        void shouldRefuseRejectionOfAnOverdueRequestLeavingTheItemsToTheJob() {
            TablePaymentRequest request = pending();

            assertRejectedWith(() -> request.reject(tab, WAITER, AFTER_WINDOW), EXPIRED);
            assertThat(request.status()).isEqualTo(TablePaymentRequestStatus.PENDING);
            assertThat(item.isReservedForPayment()).isTrue();
        }

        @Test
        void shouldTellTheUseCaseAnOverdueRequestCanNoLongerBeAnswered() {
            assertRejectedWith(() -> pending().requireAnswerableAt(AFTER_WINDOW), EXPIRED);
        }

        @Test
        void shouldLetAPendingRequestInsideItsWindowBeAnswered() {
            TablePaymentRequest request = pending();

            request.requireAnswerableAt(REQUESTED_AT.plusSeconds(60));

            assertThat(request.status()).isEqualTo(TablePaymentRequestStatus.PENDING);
        }
    }
}
