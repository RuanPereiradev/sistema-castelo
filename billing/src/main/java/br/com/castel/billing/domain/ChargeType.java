package br.com.castel.billing.domain;

import br.com.castel.billing.api.ChargeSourceType;
import br.com.castel.billing.api.FolioType;

/**
 * The kind of a {@link Charge}, which is also the value of its {@code charge_type} discriminator.
 */
public enum ChargeType {

    /** A night of a stay: {@link RoomNightCharge}. */
    ROOM_NIGHT,

    /** The total of a tab: {@link TabCharge}. */
    TAB,

    /** A correction by an {@code ADMIN}, up or down: {@link AdjustmentCharge}. */
    ADJUSTMENT;

    /** The kind of charge a posting of the given source becomes. */
    public static ChargeType of(ChargeSourceType sourceType) {
        return switch (sourceType) {
            case ROOM_NIGHT -> ROOM_NIGHT;
            case TAB -> TAB;
        };
    }

    /** A room night belongs only on the folio of a stay; the rest is accepted on any folio. */
    public boolean isAcceptedOn(FolioType folioType) {
        return this != ROOM_NIGHT || folioType == FolioType.STAY;
    }

    /**
     * Whether a charge of this kind can be reversed (decision #7 of task 1.3). An adjustment is not:
     * a wrong adjustment is corrected by another adjustment.
     */
    public boolean isReversible() {
        return this != ADJUSTMENT;
    }
}
