package br.com.castel.restaurant.domain;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.PaymentId;
import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.billing.api.ReceivedPaymentView;
import br.com.castel.sharedkernel.ConflictException;
import br.com.castel.sharedkernel.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory stand-in for the billing module behind {@link TabBilling}. It keeps a balance per folio
 * and reproduces the one billing rule the tab relies on (decision D4): a folio closes only with a
 * balance of exactly zero, refused with billing's {@code FOLIO_BALANCE_NOT_ZERO}.
 */
final class FakeTabBilling implements TabBilling {

    static final String FOLIO_BALANCE_NOT_ZERO = "FOLIO_BALANCE_NOT_ZERO";

    record Posting(FolioId folioId, TabId tabId, ChargeId chargeId, Money amount, String description) {
    }

    record Reversal(FolioId folioId, ChargeId chargeId, String reason) {
    }

    final List<FolioId> openedFolios = new ArrayList<>();
    final List<Posting> postings = new ArrayList<>();
    final List<Reversal> reversals = new ArrayList<>();
    final List<ReceivedPaymentView> payments = new ArrayList<>();
    final Set<FolioId> closedFolios = new HashSet<>();

    private final Map<FolioId, Money> balances = new HashMap<>();
    private final Map<ChargeId, Money> chargeAmounts = new HashMap<>();

    @Override
    public FolioId openFolio(TabId tabId) {
        FolioId folioId = FolioId.newId();
        openedFolios.add(folioId);
        balances.put(folioId, Money.ZERO);
        return folioId;
    }

    @Override
    public ChargeId charge(FolioId folioId, TabId tabId, Money amount, String description) {
        ChargeId chargeId = ChargeId.newId();
        postings.add(new Posting(folioId, tabId, chargeId, amount, description));
        chargeAmounts.put(chargeId, amount);
        balances.merge(folioId, amount, Money::plus);
        return chargeId;
    }

    @Override
    public void reverse(FolioId folioId, ChargeId chargeId, String reason) {
        reversals.add(new Reversal(folioId, chargeId, reason));
        balances.merge(folioId, chargeAmounts.get(chargeId), Money::minus);
    }

    @Override
    public ReceivedPaymentView receivePayment(FolioId folioId, PaymentMethod method, Money amount,
            String idempotencyKey) {
        Money balance = balances.merge(folioId, amount, Money::minus);
        ReceivedPaymentView payment = new ReceivedPaymentView(
                PaymentId.newId(), method, amount, Instant.parse("2026-09-16T16:00:00Z"), balance);
        payments.add(payment);
        return payment;
    }

    @Override
    public Money balanceOf(FolioId folioId) {
        return balances.get(folioId);
    }

    @Override
    public Money paidOn(FolioId folioId) {
        return payments.stream().map(ReceivedPaymentView::amount).reduce(Money.ZERO, Money::plus);
    }

    @Override
    public void closeFolio(FolioId folioId) {
        if (!balances.get(folioId).isZero()) {
            throw new FolioBalanceNotZero();
        }
        closedFolios.add(folioId);
    }

    /** Billing's own code, reproduced here so the test never reaches into {@code billing.domain}. */
    static final class FolioBalanceNotZero extends ConflictException {

        private static final long serialVersionUID = 1L;

        FolioBalanceNotZero() {
            super(FOLIO_BALANCE_NOT_ZERO, "The folio still owes money");
        }
    }
}
