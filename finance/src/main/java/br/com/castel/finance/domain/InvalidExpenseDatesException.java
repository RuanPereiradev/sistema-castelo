package br.com.castel.finance.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** As datas da despesa não fazem sentido entre si. */
public class InvalidExpenseDatesException extends DomainException {

    public static final String CODE = "INVALID_EXPENSE_DATES";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidExpenseDatesException(String detail) {
        super(CODE, detail);
    }
}
