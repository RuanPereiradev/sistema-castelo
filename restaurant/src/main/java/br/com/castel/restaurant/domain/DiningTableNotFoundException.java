package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** No dining table answers the given id. */
public class DiningTableNotFoundException extends NotFoundException {

    public static final String CODE = "DINING_TABLE_NOT_FOUND";

    @Serial
    private static final long serialVersionUID = 1L;

    public DiningTableNotFoundException(String detail) {
        super(CODE, detail);
    }
}
