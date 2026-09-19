package br.com.castel.billing.api;

/** What kind of thing a folio was opened for. */
public enum OwnerType {

    /** A stay: the folio belongs to a reservation of the hotel module. */
    RESERVATION,

    /** A walk-in tab: the folio belongs to a tab of the restaurant module. */
    TAB
}
