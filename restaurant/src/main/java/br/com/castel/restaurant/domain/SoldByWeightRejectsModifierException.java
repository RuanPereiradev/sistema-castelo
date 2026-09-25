package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** An item sold by weight is charged for what the scale reads, and takes no modifier. */
public class SoldByWeightRejectsModifierException extends DomainException {

    public static final String CODE = "SOLD_BY_WEIGHT_REJECTS_MODIFIER";

    @Serial
    private static final long serialVersionUID = 1L;

    public SoldByWeightRejectsModifierException(String detail) {
        super(CODE, detail);
    }
}
