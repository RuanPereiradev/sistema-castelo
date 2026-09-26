package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The self-service card already has an {@code OPEN} or {@code CLOSING} tab (decision #1), held by {@code idx_tab_open_by_card}. */
public class TabAlreadyOpenForCardException extends ConflictException {

    public static final String CODE = "TAB_ALREADY_OPEN_FOR_CARD";

    @Serial
    private static final long serialVersionUID = 1L;

    public TabAlreadyOpenForCardException(String detail) {
        super(CODE, detail);
    }
}
