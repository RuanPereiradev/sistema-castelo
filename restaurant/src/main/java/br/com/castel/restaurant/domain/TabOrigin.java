package br.com.castel.restaurant.domain;

/** How the customer is served: at a dining table, or by the self-service card. */
public enum TabOrigin {

    TABLE_SERVICE(true, true),
    SELF_SERVICE(false, false);

    private final boolean chargesServiceByDefault;
    private final boolean opensOnDiningTable;

    TabOrigin(boolean chargesServiceByDefault, boolean opensOnDiningTable) {
        this.chargesServiceByDefault = chargesServiceByDefault;
        this.opensOnDiningTable = opensOnDiningTable;
    }

    /**
     * Whether an item ordered on this origin carries the service charge, when the item itself is
     * eligible: at the table it does, at the self-service it does not.
     */
    public boolean chargesServiceByDefault() {
        return chargesServiceByDefault;
    }

    /**
     * Checks that an opening request carries the field this origin needs, and not the field of the
     * other origin: a dining table for {@code TABLE_SERVICE}, a card for {@code SELF_SERVICE}.
     *
     * @throws InvalidTabOpeningException if it does not
     */
    public void requireOpeningFields(DiningTableId diningTableId, Integer cardNumber) {
        boolean hasDiningTable = diningTableId != null;
        boolean hasCard = cardNumber != null;
        if (hasDiningTable != opensOnDiningTable || hasCard == opensOnDiningTable) {
            throw new InvalidTabOpeningException(opensOnDiningTable
                    ? "A table-service tab opens on a dining table and takes no card number"
                    : "A self-service tab opens on a card number and takes no dining table");
        }
    }
}
