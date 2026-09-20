package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The name of a menu item is blank or longer than what the column holds. */
public class InvalidMenuItemNameException extends DomainException {

    public static final String CODE = "INVALID_MENU_ITEM_NAME";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidMenuItemNameException(String detail) {
        super(CODE, detail);
    }
}
