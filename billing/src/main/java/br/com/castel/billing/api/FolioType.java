package br.com.castel.billing.api;

/** Whether a folio runs a stay or a tab of its own. */
public enum FolioType {

    /** The account of a stay, which gathers room nights and whatever the guest consumes. */
    STAY,

    /** The account of a tab of someone who is not staying at the hotel. */
    TAB
}
