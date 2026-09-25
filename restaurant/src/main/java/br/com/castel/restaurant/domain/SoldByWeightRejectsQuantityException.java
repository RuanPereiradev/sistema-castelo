package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** An item sold by weight is one plate: its quantity, when given, is 1. */
public class SoldByWeightRejectsQuantityException extends DomainException {

    public static final String CODE = "SOLD_BY_WEIGHT_REJECTS_QUANTITY";

    @Serial
    private static final long serialVersionUID = 1L;

    public SoldByWeightRejectsQuantityException(String detail) {
        super(CODE, detail);
    }
}
