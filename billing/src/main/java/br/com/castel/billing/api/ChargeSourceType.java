package br.com.castel.billing.api;

/**
 * What a posting charges for, as far as billing needs to know (decision #9 of task 1.3).
 *
 * <p>Billing tells a room night from consumption because the two are not accepted by the same
 * folios: a room night only belongs to the account of a stay.
 */
public enum ChargeSourceType {

    /** A night of a stay, posted by the hotel module. Accepted only on a {@link FolioType#STAY} folio. */
    ROOM_NIGHT,

    /** The total of a closed tab, posted by the restaurant module. Accepted on both kinds of folio. */
    TAB
}
