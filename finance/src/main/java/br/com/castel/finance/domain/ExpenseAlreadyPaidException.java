package br.com.castel.finance.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** A despesa já foi paga. */
public class ExpenseAlreadyPaidException extends ConflictException {

    public static final String CODE = "EXPENSE_ALREADY_PAID";

    @Serial
    private static final long serialVersionUID = 1L;

    public ExpenseAlreadyPaidException(String detail) {
        super(CODE, detail);
    }
}
