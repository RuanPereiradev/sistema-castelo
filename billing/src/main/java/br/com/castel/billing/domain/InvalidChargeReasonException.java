package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The reason of an adjustment or of a reversal is missing, blank or too long. */
public class InvalidChargeReasonException extends DomainException {

    public static final String CODE = "INVALID_CHARGE_REASON";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidChargeReasonException(String detail) {
        super(CODE, detail);
    }
}
