package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** No request answers the id, or it belongs to another dining table. */
public class TableOrderRequestNotFoundException extends NotFoundException {

    public static final String CODE = "TABLE_ORDER_REQUEST_NOT_FOUND";

    @Serial
    private static final long serialVersionUID = 1L;

    public TableOrderRequestNotFoundException(String detail) {
        super(CODE, detail);
    }
}
