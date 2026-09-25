package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The folio does not accept this kind of charge: a room night belongs only on a stay folio. */
public class ChargeTypeNotAcceptedException extends DomainException {

    public static final String CODE = "CHARGE_TYPE_NOT_ACCEPTED";

    @Serial
    private static final long serialVersionUID = 1L;

    public ChargeTypeNotAcceptedException(String detail) {
        super(CODE, detail);
    }
}
