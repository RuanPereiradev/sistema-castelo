package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** No folio answers the given id or reference code. */
public class FolioNotFoundException extends NotFoundException {

    public static final String CODE = "FOLIO_NOT_FOUND";

    @Serial
    private static final long serialVersionUID = 1L;

    public FolioNotFoundException(String detail) {
        super(CODE, detail);
    }
}
