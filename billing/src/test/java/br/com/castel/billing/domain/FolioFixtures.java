package br.com.castel.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.castel.billing.api.ChargeRequest;
import br.com.castel.billing.api.ChargeSource;
import br.com.castel.billing.api.ChargeSourceType;
import br.com.castel.billing.api.FolioOwner;
import br.com.castel.billing.api.FolioReference;
import br.com.castel.sharedkernel.DomainException;
import br.com.castel.sharedkernel.Money;
import java.time.Instant;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/** Shared arrangement for the folio tests, written from the spec of task 1.3 (sections 3 and 4). */
final class FolioFixtures {

    static final UUID PROPERTY_ID = UUID.randomUUID();
    static final UUID OPERATOR = UUID.randomUUID();
    static final UUID ADMIN = UUID.randomUUID();
    static final Instant OPENED_AT = Instant.parse("2026-09-25T12:00:00Z");
    static final Instant LATER = OPENED_AT.plusSeconds(3_600);

    private FolioFixtures() {}

    static Folio stayFolio() {
        return Folio.openForStay(
                PROPERTY_ID,
                FolioOwner.reservation(UUID.randomUUID()),
                new FolioReference("102", "Quarto 102 - Silva"),
                OPENED_AT);
    }

    static Folio tabFolio() {
        return Folio.openForTab(PROPERTY_ID, FolioOwner.tab(UUID.randomUUID()), OPENED_AT);
    }

    static Money money(String amount) {
        return Money.of(amount);
    }

    static ChargeRequest roomNight(String amount) {
        return new ChargeRequest(
                money(amount), "Diaria 25/09", new ChargeSource(ChargeSourceType.ROOM_NIGHT, UUID.randomUUID()));
    }

    static ChargeRequest consumption(String amount) {
        return new ChargeRequest(
                money(amount), "Comanda 17", new ChargeSource(ChargeSourceType.TAB, UUID.randomUUID()));
    }

    static ChargeRequest consumption(String amount, String description) {
        return new ChargeRequest(
                money(amount), description, new ChargeSource(ChargeSourceType.TAB, UUID.randomUUID()));
    }

    /** A manual payment with a fresh idempotency key. */
    static Payment pay(Folio folio, PaymentMethod method, String amount) {
        return folio.receive(method, money(amount), UUID.randomUUID().toString(), OPERATOR, LATER);
    }

    static Payment pay(Folio folio, String amount) {
        return pay(folio, PaymentMethod.PIX, amount);
    }

    static AdjustmentCharge adjust(Folio folio, String amount) {
        return folio.postAdjustment(money(amount), "Cortesia da gerencia", "Reclamacao do hospede", ADMIN);
    }

    /** A folio with the given consumption posted and fully paid, then closed. */
    static Folio closedTabFolio() {
        Folio folio = tabFolio();
        folio.post(consumption("50.00"));
        pay(folio, "50.00");
        folio.close(OPERATOR, LATER);
        return folio;
    }

    static void assertRejectedWith(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(DomainException.class, failure -> assertThat(failure.code()).isEqualTo(code));
    }
}
