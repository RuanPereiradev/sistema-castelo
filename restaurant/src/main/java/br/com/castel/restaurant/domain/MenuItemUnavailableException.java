package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The item is off the menu, ran out, or every active variant of it ran out (decision #5). */
public class MenuItemUnavailableException extends DomainException {

    public static final String CODE = "MENU_ITEM_UNAVAILABLE";

    @Serial
    private static final long serialVersionUID = 1L;

    public MenuItemUnavailableException(String detail) {
        super(CODE, detail);
    }
}
