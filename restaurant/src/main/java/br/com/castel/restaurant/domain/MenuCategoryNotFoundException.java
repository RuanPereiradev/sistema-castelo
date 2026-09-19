package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** No menu category answers the given id. */
public class MenuCategoryNotFoundException extends NotFoundException {

    public static final String CODE = "MENU_CATEGORY_NOT_FOUND";

    @Serial
    private static final long serialVersionUID = 1L;

    public MenuCategoryNotFoundException(String detail) {
        super(CODE, detail);
    }
}
