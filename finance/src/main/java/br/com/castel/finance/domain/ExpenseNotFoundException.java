package br.com.castel.finance.domain;

import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** Não existe despesa com esse id. */
public class ExpenseNotFoundException extends NotFoundException {

    public static final String CODE = "EXPENSE_NOT_FOUND";

    @Serial
    private static final long serialVersionUID = 1L;

    public ExpenseNotFoundException(String detail) {
        super(CODE, detail);
    }
}
