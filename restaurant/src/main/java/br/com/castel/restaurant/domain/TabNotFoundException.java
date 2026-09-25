package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** No tab answers the given id. */
public class TabNotFoundException extends NotFoundException {

    public static final String CODE = "TAB_NOT_FOUND";

    @Serial
    private static final long serialVersionUID = 1L;

    public TabNotFoundException(String detail) {
        super(CODE, detail);
    }
}
