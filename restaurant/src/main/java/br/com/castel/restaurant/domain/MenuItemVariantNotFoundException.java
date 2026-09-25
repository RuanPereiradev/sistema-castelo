package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** The item has no variant under the given id. */
public class MenuItemVariantNotFoundException extends NotFoundException {

    public static final String CODE = "MENU_ITEM_VARIANT_NOT_FOUND";

    @Serial
    private static final long serialVersionUID = 1L;

    public MenuItemVariantNotFoundException(String detail) {
        super(CODE, detail);
    }
}
