package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A tab does not open on a deactivated dining table. */
public class InactiveDiningTableException extends DomainException {

    public static final String CODE = "INACTIVE_DINING_TABLE";

    @Serial
    private static final long serialVersionUID = 1L;

    public InactiveDiningTableException(String detail) {
        super(CODE, detail);
    }
}
