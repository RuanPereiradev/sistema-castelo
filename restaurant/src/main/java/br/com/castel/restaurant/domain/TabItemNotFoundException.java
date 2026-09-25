package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** The tab has no item under the given id. */
public class TabItemNotFoundException extends NotFoundException {

    public static final String CODE = "TAB_ITEM_NOT_FOUND";

    @Serial
    private static final long serialVersionUID = 1L;

    public TabItemNotFoundException(String detail) {
        super(CODE, detail);
    }
}
