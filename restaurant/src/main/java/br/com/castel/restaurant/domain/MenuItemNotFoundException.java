package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** No menu item answers the given id. */
public class MenuItemNotFoundException extends NotFoundException {

    public static final String CODE = "MENU_ITEM_NOT_FOUND";

    @Serial
    private static final long serialVersionUID = 1L;

    public MenuItemNotFoundException(String detail) {
        super(CODE, detail);
    }
}
