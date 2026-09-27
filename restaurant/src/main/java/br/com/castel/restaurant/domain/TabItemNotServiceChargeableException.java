package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/**
 * The item was ordered without the service charge, so there is none to take off or put back
 * (decision F3).
 */
public class TabItemNotServiceChargeableException extends DomainException {

    public static final String CODE = "TAB_ITEM_NOT_SERVICE_CHARGEABLE";

    @Serial
    private static final long serialVersionUID = 1L;

    public TabItemNotServiceChargeableException(String detail) {
        super(CODE, detail);
    }
}
