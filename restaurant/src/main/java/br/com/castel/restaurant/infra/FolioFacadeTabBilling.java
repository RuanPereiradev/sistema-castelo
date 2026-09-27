package br.com.castel.restaurant.infra;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.billing.api.ChargeRequest;
import br.com.castel.billing.api.ChargeSource;
import br.com.castel.billing.api.ChargeView;
import br.com.castel.billing.api.FolioFacade;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.FolioOwner;
import br.com.castel.billing.api.FolioView;
import br.com.castel.billing.api.PaymentMethod;
import br.com.castel.billing.api.ReceivedPaymentView;
import br.com.castel.restaurant.domain.TabBilling;
import br.com.castel.restaurant.domain.TabId;
import br.com.castel.sharedkernel.Money;
import org.springframework.stereotype.Component;

/**
 * Answers the {@link TabBilling} port over the {@link FolioFacade}, the only door of the billing
 * module. Every call joins the transaction of the caller, so the tab and its folio commit together.
 *
 * <p>Billing's refusals pass through untouched: they already carry the stable code the front
 * translates.
 */
@Component
class FolioFacadeTabBilling implements TabBilling {

    private final FolioFacade folios;

    FolioFacadeTabBilling(FolioFacade folios) {
        this.folios = folios;
    }

    @Override
    public FolioId openFolio(TabId tabId) {
        return folios.openTabFolio(FolioOwner.tab(tabId.value()));
    }

    /** The source of the charge is the tab itself, so billing can trace the posting back to it. */
    @Override
    public ChargeId charge(FolioId folioId, TabId tabId, Money amount, String description) {
        return folios.post(folioId, new ChargeRequest(amount, description, ChargeSource.tab(tabId.value())));
    }

    @Override
    public void reverse(FolioId folioId, ChargeId chargeId, String reason) {
        folios.reverse(folioId, chargeId, reason);
    }

    @Override
    public ReceivedPaymentView receivePayment(
            FolioId folioId, PaymentMethod method, Money amount, String idempotencyKey) {
        return folios.receivePayment(folioId, method, amount, idempotencyKey);
    }

    @Override
    public Money balanceOf(FolioId folioId) {
        return folios.balanceOf(folioId);
    }

    @Override
    public Money paidOn(FolioId folioId) {
        FolioView folio = folios.findById(folioId);
        return folio.charges().stream()
                .map(ChargeView::amount)
                .reduce(Money.ZERO, Money::plus)
                .minus(folio.balance());
    }

    @Override
    public void closeFolio(FolioId folioId) {
        folios.close(folioId);
    }
}
