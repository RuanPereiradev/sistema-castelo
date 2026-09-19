package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** Another category of the same property already carries this name. */
public class DuplicateMenuCategoryNameException extends ConflictException {

    public static final String CODE = "MENU_CATEGORY_NAME_ALREADY_USED";

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateMenuCategoryNameException(String detail) {
        super(CODE, detail);
    }
}
