package br.com.castel.billing.api;

/** Whether a folio still takes postings. */
public enum FolioStatus {

    OPEN,
    CLOSED;

    /** A closed folio takes no charge and no payment. */
    public boolean acceptsPostings() {
        return this == OPEN;
    }
}
