package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The charge was already reversed; a charge is reversed once (decision #7 of task 1.3). */
public class ChargeAlreadyReversedException extends ConflictException {

    public static final String CODE = "CHARGE_ALREADY_REVERSED";

    @Serial
    private static final long serialVersionUID = 1L;

    public ChargeAlreadyReversedException(String detail) {
        super(CODE, detail);
    }
}
