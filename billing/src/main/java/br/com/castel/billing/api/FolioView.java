package br.com.castel.billing.api;

import br.com.castel.sharedkernel.Money;
import java.util.Collections;
import java.util.List;

/**
 * A folio as another module reads it: the header, the postings and the balance.
 *
 * <p>The postings travel with it, unpaged: it is what the front desk needs on the screen at
 * check-out, and a stay posts one charge per night plus one per tab (decisions #3 and #6).
 */
public record FolioView(
        FolioId folioId,
        FolioType type,
        FolioStatus status,
        FolioOwner owner,
        FolioReference reference,
        List<ChargeView> charges,
        Money balance) {

    public FolioView {
        charges = List.copyOf(charges);
    }

    @Override
    public List<ChargeView> charges() {
        return Collections.unmodifiableList(charges);
    }
}
