package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** Another variant of the same item already carries this name, ignoring letter case. */
public class DuplicateMenuItemVariantNameException extends ConflictException {

    public static final String CODE = "MENU_ITEM_VARIANT_NAME_ALREADY_USED";

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateMenuItemVariantNameException(String detail) {
        super(CODE, detail);
    }
}
