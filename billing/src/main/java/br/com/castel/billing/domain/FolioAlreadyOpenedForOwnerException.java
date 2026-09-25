package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The owner already has a folio: there is one folio per reservation and one per tab. */
public class FolioAlreadyOpenedForOwnerException extends ConflictException {

    public static final String CODE = "FOLIO_ALREADY_OPENED_FOR_OWNER";

    @Serial
    private static final long serialVersionUID = 1L;

    public FolioAlreadyOpenedForOwnerException(String detail) {
        super(CODE, detail);
    }
}
