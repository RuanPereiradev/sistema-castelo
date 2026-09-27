package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.TabClosingView;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabBill;
import br.com.castel.sharedkernel.Money;
import br.com.castel.sharedkernel.Percentage;
import java.util.List;

/**
 * The pre-bill as the waiter reads it: totals, the service charge and its rate, what was paid, the
 * split groups and an even split.
 *
 * <p>{@code serviceChargeRate} travels in percent points ({@code "10.00"}), the unit of the setting.
 * {@code paid} and {@code balance} are null while the tab has no folio.
 *
 * <p>The even split (decision D11): with {@code parts} asked for, it divides the total — or the
 * total of one group, with {@code splitGroup} — and parts that do not fit are refused; without,
 * {@code guestCount} is the default when it fits, and nothing is shown otherwise, so the pre-bill
 * never fails for a default nobody asked for. {@code balanceEvenSplit} divides what is still owed the
 * same way, when something is and it fits; never for a group, since payments are not tied to groups
 * (decision F7).
 */
public record TabBillResponse(
        String tabId,
        String status,
        Integer guestCount,
        boolean serviceChargeApplied,
        String subtotal,
        String serviceChargeBase,
        String serviceChargeRate,
        String serviceCharge,
        String total,
        String paid,
        String balance,
        List<SplitGroupResponse> groups,
        Integer evenSplitParts,
        Integer evenSplitGroup,
        List<String> evenSplit,
        List<String> balanceEvenSplit) {

    /** The pre-bill with the default even split: the guest count, when it fits. */
    public static TabBillResponse from(TabClosingView view) {
        return from(view, null, null);
    }

    /**
     * @throws br.com.castel.restaurant.domain.InvalidSplitPartsException if {@code parts} was asked
     *     for and does not fit
     * @throws br.com.castel.restaurant.domain.InvalidSplitGroupException if {@code splitGroup} holds
     *     no active item
     */
    public static TabBillResponse from(TabClosingView view, Integer parts, Integer splitGroup) {
        Tab tab = view.tab();
        TabBill bill = view.bill();
        Money target = splitGroup == null ? bill.total() : bill.group(splitGroup).total();
        Integer chosenParts = parts != null
                ? Integer.valueOf(parts)
                : tab.guestCount().filter(count -> TabBill.acceptsEvenSplit(target, count)).orElse(null);
        List<String> evenSplit = chosenParts == null ? null : asStrings(TabBill.evenSplit(target, chosenParts));
        List<String> balanceEvenSplit = chosenParts == null || splitGroup != null
                ? null
                : view.balance()
                        .filter(balance -> TabBill.acceptsEvenSplit(balance, chosenParts))
                        .map(balance -> asStrings(TabBill.evenSplit(balance, chosenParts)))
                        .orElse(null);
        return new TabBillResponse(
                tab.id().value().toString(),
                tab.status().name(),
                tab.guestCount().orElse(null),
                tab.serviceChargeApplied(),
                bill.subtotal().asString(),
                bill.serviceChargeBase().asString(),
                percentPoints(bill.serviceChargeRate()),
                bill.serviceCharge().asString(),
                bill.total().asString(),
                view.paid().map(Money::asString).orElse(null),
                view.balance().map(Money::asString).orElse(null),
                bill.groups().stream().map(SplitGroupResponse::from).toList(),
                chosenParts,
                splitGroup,
                evenSplit,
                balanceEvenSplit);
    }

    /** {@code 0.1000} as {@code "10.00"}. */
    static String percentPoints(Percentage rate) {
        return rate.fraction().movePointRight(2).toPlainString();
    }

    private static List<String> asStrings(List<Money> amounts) {
        return amounts.stream().map(Money::asString).toList();
    }

    /** One split group: its subtotal, its share of the service charge and its total. */
    public record SplitGroupResponse(
            int splitGroup,
            String subtotal,
            String serviceChargeBase,
            String serviceCharge,
            String total,
            List<String> itemIds) {

        static SplitGroupResponse from(TabBill.SplitGroup group) {
            return new SplitGroupResponse(
                    group.splitGroup(),
                    group.subtotal().asString(),
                    group.serviceChargeBase().asString(),
                    group.serviceCharge().asString(),
                    group.total().asString(),
                    group.itemIds().stream().map(id -> id.value().toString()).toList());
        }
    }
}
