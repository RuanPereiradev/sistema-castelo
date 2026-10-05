package br.com.castel.finance.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** O valor de uma despesa é maior que zero. */
public class InvalidExpenseAmountException extends DomainException {

    public static final String CODE = "INVALID_EXPENSE_AMOUNT";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidExpenseAmountException(String detail) {
        super(CODE, detail);
    }
}
