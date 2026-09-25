package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** A dining table with an {@code OPEN} or {@code CLOSING} tab cannot be deactivated (decision #8 of task 2.2). */
public class DiningTableHasOpenTabException extends ConflictException {

    public static final String CODE = "DINING_TABLE_HAS_OPEN_TAB";

    @Serial
    private static final long serialVersionUID = 1L;

    public DiningTableHasOpenTabException(String detail) {
        super(CODE, detail);
    }
}
