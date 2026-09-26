package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The reference of a folio is missing, or its code or label is blank or too long. */
public class InvalidFolioReferenceException extends DomainException {

    public static final String CODE = "INVALID_FOLIO_REFERENCE";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidFolioReferenceException(String detail) {
        super(CODE, detail);
    }
}
