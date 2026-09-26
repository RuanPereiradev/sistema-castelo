package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The folio is closed and takes no further write. */
public class FolioClosedException extends ConflictException {

    public static final String CODE = "FOLIO_CLOSED";

    @Serial
    private static final long serialVersionUID = 1L;

    public FolioClosedException(String detail) {
        super(CODE, detail);
    }
}
