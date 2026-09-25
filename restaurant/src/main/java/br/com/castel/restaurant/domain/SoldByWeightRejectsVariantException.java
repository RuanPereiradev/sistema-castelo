package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** An item sold by weight is priced by the kilo, and a variant would bring a second price. */
public class SoldByWeightRejectsVariantException extends DomainException {

    public static final String CODE = "SOLD_BY_WEIGHT_REJECTS_VARIANT";

    @Serial
    private static final long serialVersionUID = 1L;

    public SoldByWeightRejectsVariantException(String detail) {
        super(CODE, detail);
    }
}
