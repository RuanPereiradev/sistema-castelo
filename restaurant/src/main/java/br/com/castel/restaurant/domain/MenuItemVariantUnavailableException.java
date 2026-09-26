package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The variant ordered is off the menu or ran out. */
public class MenuItemVariantUnavailableException extends DomainException {

    public static final String CODE = "MENU_ITEM_VARIANT_UNAVAILABLE";

    @Serial
    private static final long serialVersionUID = 1L;

    public MenuItemVariantUnavailableException(String detail) {
        super(CODE, detail);
    }
}
