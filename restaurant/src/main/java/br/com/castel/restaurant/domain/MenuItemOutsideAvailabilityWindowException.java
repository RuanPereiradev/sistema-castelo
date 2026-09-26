package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The item is not served at this hour of the property (decision #5). */
public class MenuItemOutsideAvailabilityWindowException extends DomainException {

    public static final String CODE = "MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW";

    @Serial
    private static final long serialVersionUID = 1L;

    public MenuItemOutsideAvailabilityWindowException(String detail) {
        super(CODE, detail);
    }
}
