package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** The folio has no charge with the given id. */
public class ChargeNotFoundException extends NotFoundException {

    public static final String CODE = "CHARGE_NOT_FOUND";

    @Serial
    private static final long serialVersionUID = 1L;

    public ChargeNotFoundException(String detail) {
        super(CODE, detail);
    }
}
