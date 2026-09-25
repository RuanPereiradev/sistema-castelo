package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The name of a variant is blank or longer than what the column holds. */
public class InvalidMenuItemVariantNameException extends DomainException {

    public static final String CODE = "INVALID_MENU_ITEM_VARIANT_NAME";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidMenuItemVariantNameException(String detail) {
        super(CODE, detail);
    }
}
