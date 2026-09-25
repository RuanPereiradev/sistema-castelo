package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** An item sold by weight is ordered by its weight in grams. */
public class SoldByWeightRequiresWeightException extends DomainException {

    public static final String CODE = "SOLD_BY_WEIGHT_REQUIRES_WEIGHT";

    @Serial
    private static final long serialVersionUID = 1L;

    public SoldByWeightRequiresWeightException(String detail) {
        super(CODE, detail);
    }
}
