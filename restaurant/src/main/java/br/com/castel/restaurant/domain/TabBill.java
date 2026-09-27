package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.Money;
import br.com.castel.sharedkernel.Percentage;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The pre-bill of a tab: what it costs, what the service charge adds, and how the bill splits.
 *
 * <p>Calculated from the tab on every read, never stored. The service charge is calculated once over
 * the sum of what counts for it, never item by item, and each split group takes its share of that one
 * amount by the largest remainder (decision F4), so the groups always add up to the tab.
 *
 * @param serviceChargeRate the rate in force: frozen once closing started, the current one before
 * @param groups one per split group holding an active item, by group number
 */
public record TabBill(
        Money subtotal,
        Money serviceChargeBase,
        Percentage serviceChargeRate,
        Money serviceCharge,
        Money total,
        List<SplitGroup> groups) {

    public static final int MINIMUM_SPLIT_PARTS = 1;
    public static final int MAXIMUM_SPLIT_PARTS = 99;

    public TabBill {
        Objects.requireNonNull(subtotal, "subtotal");
        Objects.requireNonNull(serviceChargeBase, "serviceChargeBase");
        Objects.requireNonNull(serviceChargeRate, "serviceChargeRate");
        Objects.requireNonNull(serviceCharge, "serviceCharge");
        Objects.requireNonNull(total, "total");
        groups = List.copyOf(groups);
    }

    /**
     * The bill of the given items, with the service charge the tab already calculated over the sum.
     * Receives values the {@link Tab} already worked out.
     */
    static TabBill of(List<TabItem> items, boolean serviceChargeApplied, Money subtotal, Money serviceChargeBase,
            Percentage rate, Money serviceCharge) {
        List<TabItem> active = items.stream().filter(TabItem::isActive).toList();
        List<Integer> groupNumbers = active.stream().map(TabItem::splitGroup).distinct().sorted().toList();
        List<Money> bases = groupNumbers.stream()
                .map(group -> active.stream()
                        .filter(item -> item.splitGroup() == group && serviceChargeApplied && item.countsForServiceCharge())
                        .map(TabItem::lineTotal)
                        .reduce(Money.ZERO, Money::plus))
                .toList();
        List<Money> shares = shareByLargestRemainder(serviceCharge, bases);
        List<SplitGroup> groups = new ArrayList<>();
        for (int index = 0; index < groupNumbers.size(); index++) {
            int group = groupNumbers.get(index);
            List<TabItem> ofGroup = active.stream().filter(item -> item.splitGroup() == group).toList();
            Money groupSubtotal = ofGroup.stream().map(TabItem::lineTotal).reduce(Money.ZERO, Money::plus);
            groups.add(new SplitGroup(group, groupSubtotal, bases.get(index), shares.get(index),
                    groupSubtotal.plus(shares.get(index)), ofGroup.stream().map(TabItem::id).toList()));
        }
        return new TabBill(subtotal, serviceChargeBase, rate, serviceCharge, subtotal.plus(serviceCharge), groups);
    }

    /** The total in {@code parts} shares of whole cents; the first shares take the extra cent. */
    public List<Money> evenSplit(int parts) {
        return evenSplit(total, parts);
    }

    /**
     * @throws InvalidSplitGroupException if no active item is in that group
     */
    public SplitGroup group(int splitGroup) {
        return groups.stream()
                .filter(group -> group.splitGroup() == splitGroup)
                .findFirst()
                .orElseThrow(() -> new InvalidSplitGroupException("No active item is in split group " + splitGroup));
    }

    /**
     * {@code amount} in {@code parts} shares of whole cents. With {@code q = cents / parts} and
     * {@code r = cents % parts}, the first {@code r} shares take {@code q + 0.01} (decision F4): the
     * shares add up to the amount and no two differ by more than one cent.
     *
     * @throws InvalidSplitPartsException if the parts fall outside 1 to 99, or outnumber the cents of
     *     the amount, which would leave a share of zero
     */
    public static List<Money> evenSplit(Money amount, int parts) {
        Objects.requireNonNull(amount, "amount");
        if (!acceptsEvenSplit(amount, parts)) {
            throw new InvalidSplitPartsException("An even split goes from " + MINIMUM_SPLIT_PARTS + " to "
                    + MAXIMUM_SPLIT_PARTS + " parts, and never into more parts than the amount has cents");
        }
        BigInteger[] quotientAndRemainder = cents(amount).divideAndRemainder(BigInteger.valueOf(parts));
        long quotient = quotientAndRemainder[0].longValueExact();
        int remainder = quotientAndRemainder[1].intValueExact();
        List<Money> shares = new ArrayList<>();
        for (int share = 0; share < parts; share++) {
            shares.add(Money.ofCents(share < remainder ? quotient + 1 : quotient));
        }
        return shares;
    }

    /** Whether {@code amount} splits evenly into {@code parts}: 1 to 99 parts, no share of zero. */
    public static boolean acceptsEvenSplit(Money amount, int parts) {
        Objects.requireNonNull(amount, "amount");
        return parts >= MINIMUM_SPLIT_PARTS
                && parts <= MAXIMUM_SPLIT_PARTS
                && cents(amount).compareTo(BigInteger.valueOf(parts)) >= 0;
    }

    /**
     * Shares {@code amount} in proportion to {@code weights}: each takes the floor of its exact share
     * in cents, and the cents left over go one by one to the largest remainders, the earlier weight
     * first on a tie. When every weight is zero, every share is zero.
     */
    private static List<Money> shareByLargestRemainder(Money amount, List<Money> weights) {
        BigInteger total = cents(amount);
        BigInteger weightSum = weights.stream().map(TabBill::cents).reduce(BigInteger.ZERO, BigInteger::add);
        List<Money> shares = new ArrayList<>();
        if (weightSum.signum() == 0) {
            weights.forEach(weight -> shares.add(Money.ZERO));
            return shares;
        }
        long[] floors = new long[weights.size()];
        BigInteger[] remainders = new BigInteger[weights.size()];
        long distributed = 0;
        for (int index = 0; index < weights.size(); index++) {
            BigInteger[] division = total.multiply(cents(weights.get(index))).divideAndRemainder(weightSum);
            floors[index] = division[0].longValueExact();
            remainders[index] = division[1];
            distributed += floors[index];
        }
        long leftover = total.longValueExact() - distributed;
        List<Integer> byLargestRemainder = new ArrayList<>();
        for (int index = 0; index < weights.size(); index++) {
            byLargestRemainder.add(index);
        }
        byLargestRemainder.sort(Comparator.<Integer, BigInteger>comparing(index -> remainders[index])
                .reversed()
                .thenComparing(Comparator.naturalOrder()));
        for (int rank = 0; rank < leftover; rank++) {
            floors[byLargestRemainder.get(rank)]++;
        }
        for (long floor : floors) {
            shares.add(Money.ofCents(floor));
        }
        return shares;
    }

    private static BigInteger cents(Money money) {
        return money.amount().movePointRight(2).toBigIntegerExact();
    }

    /**
     * One split group of the bill: what its items cost, its share of the service charge and its total.
     *
     * @param serviceChargeBase what of the group counts for the service charge
     * @param itemIds the active items of the group, in the order of the tab
     */
    public record SplitGroup(
            int splitGroup,
            Money subtotal,
            Money serviceChargeBase,
            Money serviceCharge,
            Money total,
            List<TabItemId> itemIds) {

        public SplitGroup {
            Objects.requireNonNull(subtotal, "subtotal");
            Objects.requireNonNull(serviceChargeBase, "serviceChargeBase");
            Objects.requireNonNull(serviceCharge, "serviceCharge");
            Objects.requireNonNull(total, "total");
            itemIds = List.copyOf(itemIds);
        }

        /** The total of the group in {@code parts} shares, the way the whole bill splits (decision F5). */
        public List<Money> evenSplit(int parts) {
            return TabBill.evenSplit(total, parts);
        }
    }
}
