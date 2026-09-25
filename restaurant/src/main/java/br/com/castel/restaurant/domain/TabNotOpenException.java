package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The tab is not {@code OPEN}, so it takes no item, no item cancellation and no cancellation of its own. */
public class TabNotOpenException extends ConflictException {

    public static final String CODE = "TAB_NOT_OPEN";

    @Serial
    private static final long serialVersionUID = 1L;

    public TabNotOpenException(String detail) {
        super(CODE, detail);
    }
}
