package br.com.castel.restaurant.application;

import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabBill;
import br.com.castel.sharedkernel.Money;
import java.util.List;
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

    /**
     * The even split shown with the pre-bill (decision D11). With {@code parts} asked for, it divides
     * the total, or the total of one {@code splitGroup}, and parts that do not fit are refused; without,
     * the guest count is the default when it fits, and nothing is shown otherwise, so the pre-bill
     * never fails for a default nobody asked for. What is still owed splits the same way, when
     * something is and it fits; never for a group, since payments are not tied to groups (F7).
     *
     * @param parts null for the default
     * @param splitGroup null for the whole tab
     * @throws br.com.castel.restaurant.domain.InvalidSplitPartsException if {@code parts} does not fit
     * @throws br.com.castel.restaurant.domain.InvalidSplitGroupException if the group holds no active item
     */
    public EvenSplit evenSplit(Integer parts, Integer splitGroup) {
        Money target = splitGroup == null ? bill.total() : bill.group(splitGroup).total();
        Optional<Integer> chosenParts = Optional.ofNullable(parts)
                .or(() -> tab.guestCount().filter(count -> TabBill.acceptsEvenSplit(target, count)));
        Optional<List<Money>> shares = chosenParts.map(count -> TabBill.evenSplit(target, count));
        Optional<List<Money>> balanceShares = chosenParts
                .filter(count -> splitGroup == null)
                .flatMap(count -> balance
                        .filter(owed -> TabBill.acceptsEvenSplit(owed, count))
                        .map(owed -> TabBill.evenSplit(owed, count)));
        return new EvenSplit(chosenParts, Optional.ofNullable(splitGroup), shares, balanceShares);
    }

    /**
     * An even split of the pre-bill: how many parts, of which group, the shares of the total and the
     * shares of what is still owed. Each part is empty when there is nothing to show.
     */
    public record EvenSplit(
            Optional<Integer> parts,
            Optional<Integer> splitGroup,
            Optional<List<Money>> shares,
            Optional<List<Money>> balanceShares) {
    }
}
