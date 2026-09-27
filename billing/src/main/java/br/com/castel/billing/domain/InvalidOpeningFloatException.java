package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The opening float is missing or below zero. */
public class InvalidOpeningFloatException extends DomainException {

    public static final String CODE = "INVALID_OPENING_FLOAT";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidOpeningFloatException(String detail) {
        super(CODE, detail);
    }
}
