package br.com.castel.restaurant.application;

import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabBill;
import br.com.castel.sharedkernel.Money;
import java.util.Objects;
import java.util.Optional;

/**
 * A tab with its pre-bill and, once it has a folio, what was paid and what is still owed.
 *
 * @param paid empty while the tab has no folio
 * @param balance empty while the tab has no folio
 */
public record TabClosingView(Tab tab, TabBill bill, Optional<Money> paid, Optional<Money> balance) {

    public TabClosingView {
        Objects.requireNonNull(tab, "tab");
        Objects.requireNonNull(bill, "bill");
        Objects.requireNonNull(paid, "paid");
        Objects.requireNonNull(balance, "balance");
    }
}
