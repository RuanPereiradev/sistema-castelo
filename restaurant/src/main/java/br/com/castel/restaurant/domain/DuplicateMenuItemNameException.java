package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** Another item of the same property already carries this name. */
public class DuplicateMenuItemNameException extends ConflictException {

    public static final String CODE = "MENU_ITEM_NAME_ALREADY_USED";

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateMenuItemNameException(String detail) {
        super(CODE, detail);
    }
}
