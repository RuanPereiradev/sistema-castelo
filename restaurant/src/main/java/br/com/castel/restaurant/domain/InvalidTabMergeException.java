package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A tab was asked to be merged into itself. */
public class InvalidTabMergeException extends DomainException {

    public static final String CODE = "INVALID_TAB_MERGE";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidTabMergeException(String detail) {
        super(CODE, detail);
    }
}
