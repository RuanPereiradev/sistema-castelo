package br.com.castel.taxinvoice.infra;

import br.com.castel.sharedkernel.NotFoundException;
import br.com.castel.taxinvoice.api.AccessKey;
import java.io.Serial;

/** The fake issuer was asked to cancel an invoice it never issued. */
public class UnknownTaxInvoiceException extends NotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "TAX_INVOICE_NOT_FOUND";

    public UnknownTaxInvoiceException(AccessKey accessKey) {
        super(CODE, "Tax invoice not found: " + accessKey.value());
    }
}
