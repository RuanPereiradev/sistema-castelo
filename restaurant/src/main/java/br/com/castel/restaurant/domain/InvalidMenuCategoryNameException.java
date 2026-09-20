package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The name of a menu category is blank or longer than what the column holds. */
public class InvalidMenuCategoryNameException extends DomainException {

    public static final String CODE = "INVALID_MENU_CATEGORY_NAME";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidMenuCategoryNameException(String detail) {
        super(CODE, detail);
    }
}
