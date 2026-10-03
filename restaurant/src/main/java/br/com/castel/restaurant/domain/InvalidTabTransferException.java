package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A transfer was asked for with no item, or with the source tab as its own destination. */
public class InvalidTabTransferException extends DomainException {

    public static final String CODE = "INVALID_TAB_TRANSFER";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidTabTransferException(String detail) {
        super(CODE, detail);
    }
}
