package br.com.castel.billing.api;

import br.com.castel.sharedkernel.Money;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A folio as another module reads it: the header, the postings and the balance.
 *
 * <p>The postings travel with it, unpaged: it is what the front desk needs on the screen at
 * check-out, and a stay posts one charge per night plus one per tab (decisions #3 and #6).
 *
 * <p>{@code reference} is empty for the folio of a tab, which has none: a {@code null} component is
 * what decision #12 of task 0.6 forbids (decision #10 of task 1.3).
 */
public record FolioView(
        FolioId folioId,
        FolioType type,
        FolioStatus status,
        FolioOwner owner,
        Optional<FolioReference> reference,
        List<ChargeView> charges,
        Money balance) {

    public FolioView {
        Objects.requireNonNull(reference, "reference");
        charges = List.copyOf(charges);
    }

    @Override
    public List<ChargeView> charges() {
        return Collections.unmodifiableList(charges);
    }
}
