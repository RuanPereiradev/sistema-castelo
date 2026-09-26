package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** A folio closes only with a balance of exactly zero (decision #4 of task 1.3). */
public class FolioBalanceNotZeroException extends ConflictException {

    public static final String CODE = "FOLIO_BALANCE_NOT_ZERO";

    @Serial
    private static final long serialVersionUID = 1L;

    public FolioBalanceNotZeroException(String detail) {
        super(CODE, detail);
    }
}
