package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** Only a room night or tab charge that is not itself a reversal can be reversed (decision #7 of task 1.3). */
public class ChargeNotReversibleException extends DomainException {

    public static final String CODE = "CHARGE_NOT_REVERSIBLE";

    @Serial
    private static final long serialVersionUID = 1L;

    public ChargeNotReversibleException(String detail) {
        super(CODE, detail);
    }
}
