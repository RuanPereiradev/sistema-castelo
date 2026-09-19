package br.com.castel.taxinvoice.api;

/**
 * Whoever issues tax invoices for the property, as the system sees it.
 *
 * <p>A port with a fake adapter in version 1, so the system can run the whole operation before
 * anyone decides how the fiscal document is issued.
 */
public interface TaxInvoiceIssuer {

    IssuedInvoice issue(TaxInvoiceRequest request);

    /** Cancels an issued invoice. The reason travels because the tax authority asks for one. */
    void cancel(AccessKey accessKey, String reason);
}
