package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** An even split goes from 1 to 99 parts, and never into more parts than the amount has cents. */
public class InvalidSplitPartsException extends DomainException {

    public static final String CODE = "INVALID_SPLIT_PARTS";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidSplitPartsException(String detail) {
        super(CODE, detail);
    }
}
