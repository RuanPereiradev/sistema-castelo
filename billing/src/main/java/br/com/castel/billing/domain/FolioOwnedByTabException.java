package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/**
 * The folio belongs to a tab, and only the tab reverses its charge and closes it: through its
 * reopening, its close and its cancellation (decision R1 of task 3.2). The counter still receives
 * payments on it, refunds them and posts adjustments.
 */
public class FolioOwnedByTabException extends ConflictException {

    public static final String CODE = "FOLIO_OWNED_BY_TAB";

    @Serial
    private static final long serialVersionUID = 1L;

    public FolioOwnedByTabException(String detail) {
        super(CODE, detail);
    }
}
