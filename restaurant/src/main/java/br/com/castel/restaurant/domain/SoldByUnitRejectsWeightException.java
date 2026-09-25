package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** An item sold by unit takes no weight. */
public class SoldByUnitRejectsWeightException extends DomainException {

    public static final String CODE = "SOLD_BY_UNIT_REJECTS_WEIGHT";

    @Serial
    private static final long serialVersionUID = 1L;

    public SoldByUnitRejectsWeightException(String detail) {
        super(CODE, detail);
    }
}
