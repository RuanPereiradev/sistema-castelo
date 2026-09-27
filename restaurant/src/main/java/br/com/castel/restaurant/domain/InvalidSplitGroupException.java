package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A split group goes from 1 to 99, and a group asked for by number must hold an active item. */
public class InvalidSplitGroupException extends DomainException {

    public static final String CODE = "INVALID_SPLIT_GROUP";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidSplitGroupException(String detail) {
        super(CODE, detail);
    }
}
