package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/**
 * Another open stay folio already answers this reference code (decision #17 of task 1.3). The detail
 * is fixed and does not echo the code typed.
 */
public class FolioReferenceAlreadyInUseException extends ConflictException {

    public static final String CODE = "FOLIO_REFERENCE_ALREADY_IN_USE";

    @Serial
    private static final long serialVersionUID = 1L;

    public FolioReferenceAlreadyInUseException() {
        super(CODE, "Another open stay folio uses this reference code");
    }
}
