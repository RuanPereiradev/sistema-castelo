package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** An item sold by weight needs a price per kilo and refuses a unit price, and the other way round. */
public class InvalidMenuItemPricingException extends DomainException {

    public static final String CODE = "INVALID_MENU_ITEM_PRICING";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidMenuItemPricingException(String detail) {
        super(CODE, detail);
    }
}
