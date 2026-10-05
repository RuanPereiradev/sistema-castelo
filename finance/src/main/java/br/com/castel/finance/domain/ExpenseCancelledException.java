package br.com.castel.finance.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** A despesa foi cancelada e não aceita mais mudança. */
public class ExpenseCancelledException extends ConflictException {

    public static final String CODE = "EXPENSE_CANCELLED";

    @Serial
    private static final long serialVersionUID = 1L;

    public ExpenseCancelledException(String detail) {
        super(CODE, detail);
    }
}
