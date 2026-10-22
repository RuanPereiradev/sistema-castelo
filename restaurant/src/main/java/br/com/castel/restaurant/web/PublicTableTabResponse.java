package br.com.castel.restaurant.web;

import br.com.castel.restaurant.application.TablePaymentTabView;
import br.com.castel.restaurant.domain.Tab;
import br.com.castel.restaurant.domain.TabBill;
import br.com.castel.restaurant.domain.TabItem;
import java.util.List;

/**
 * The active tab of the table as its phone reads it (decision #1 of task qr-table-payment): the items
 * not cancelled, what each costs and whether it is paid or held by a payment waiting for the waiter,
 * and the totals. No id of the tab, no user, no preparation station.
 *
 * <p>Each item says how many of its units are paid and how many a payment holds (decision #39), who
 * ordered it from the phone, and {@code mine} when the device reading ordered it (decision #40). The
 * device code of anybody is never sent back.
 *
 * @param unpaidServiceChargeBase the base the rate still applies to: the units not paid that count for
 *     the charge; times the rate, plus the shares frozen in the payments, it is {@code serviceCharge}
 * @param paidAmount what the phone already paid, items and their frozen share of the service charge
 * @param amountDue the total less {@code paidAmount}
 * @param acceptsPayment false while no Pix receiver is configured (decision #15)
 */
public record PublicTableTabResponse(
        String diningTableLabel,
        String status,
        List<Item> items,
        String subtotal,
        String unpaidServiceChargeBase,
        String serviceChargeRate,
        String serviceCharge,
        String total,
        String paidAmount,
        String amountDue,
        boolean acceptsPayment) {

    /** One item: its frozen name, variant, modifiers and value, and its kitchen status. */
    public record Item(
            String id,
            String itemName,
            String variantName,
            List<Modifier> modifiers,
            int quantity,
            Integer weightGrams,
            String lineTotal,
            String status,
            boolean paid,
            int paidQuantity,
            int reservedQuantity,
            String customerName,
            boolean mine) {

        static Item from(TabItem item, boolean mine) {
            return new Item(
                    item.id().value().toString(),
                    item.itemName(),
                    item.variantName().orElse(null),
                    item.modifiers().stream().map(modifier -> new Modifier(modifier.modifierName(), modifier.quantity()))
                            .toList(),
                    item.quantity(),
                    item.weight().map(weight -> weight.grams()).orElse(null),
                    item.lineTotal().asString(),
                    item.status().name(),
                    item.isPaid(),
                    item.paidQuantity(),
                    item.reservedQuantity(),
                    item.customerName().orElse(null),
                    mine);
        }
    }

    public record Modifier(String name, int quantity) {
    }

    static PublicTableTabResponse from(TablePaymentTabView view) {
        Tab tab = view.tab();
        TabBill bill = tab.bill(view.currentRate());
        return new PublicTableTabResponse(
                view.diningTable().label(),
                tab.status().name(),
                tab.items().stream()
                        .filter(TabItem::isActive)
                        .map(item -> Item.from(item, view.isOfReadingDevice(item)))
                        .toList(),
                bill.subtotal().asString(),
                bill.unpaidServiceChargeBase().asString(),
                TabBillResponse.percentPoints(bill.serviceChargeRate()),
                bill.serviceCharge().asString(),
                bill.total().asString(),
                bill.paidAmount().asString(),
                bill.amountDue().asString(),
                view.acceptsPayment());
    }
}
