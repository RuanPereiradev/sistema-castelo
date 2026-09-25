package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The item has active variants, and the order named none. */
public class MenuItemVariantRequiredException extends DomainException {

    public static final String CODE = "MENU_ITEM_VARIANT_REQUIRED";

    @Serial
    private static final long serialVersionUID = 1L;

    public MenuItemVariantRequiredException(String detail) {
        super(CODE, detail);
    }
}
