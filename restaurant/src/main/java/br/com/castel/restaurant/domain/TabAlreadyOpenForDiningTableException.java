package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The dining table already has an {@code OPEN} or {@code CLOSING} tab (decision #1), held by {@code idx_tab_open_by_table}. */
public class TabAlreadyOpenForDiningTableException extends ConflictException {

    public static final String CODE = "TAB_ALREADY_OPEN_FOR_DINING_TABLE";

    @Serial
    private static final long serialVersionUID = 1L;

    public TabAlreadyOpenForDiningTableException(String detail) {
        super(CODE, detail);
    }
}
