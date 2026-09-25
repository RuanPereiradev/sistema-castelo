package br.com.castel.billing.api;

import br.com.castel.sharedkernel.Money;
import java.util.Objects;

/**
 * One posting a module asks the billing module to write on a folio.
 *
 * <p>A closed tab posts a single charge with its total and a description naming it — not one charge
 * per item (decision #3). The bill of a room stays readable, and whoever wants the consumption item
 * by item reads the tab, which keeps it.
 *
 * <p>The {@link ChargeSource} says whether the posting is a room night or consumption, and which one
 * (decision #9 of task 1.3): billing needs the first to refuse a room night on the folio of a tab,
 * and keeps the second so a posting can be traced back to what produced it.
 *
 * <p>No author travels here. Every audited row already records {@code created_by} from the
 * authenticated user of the request (task 0.5b), so carrying the operator again would be a second
 * copy of the same fact, free to disagree with the first (decision #9 of task 0.6).
 */
public record ChargeRequest(Money amount, String description, ChargeSource source) {

    public ChargeRequest {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(source, "source");
    }
}
