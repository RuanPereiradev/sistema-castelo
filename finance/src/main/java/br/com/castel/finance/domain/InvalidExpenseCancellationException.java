package br.com.castel.finance.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** O cancelamento de uma despesa precisa de motivo. */
public class InvalidExpenseCancellationException extends DomainException {

    public static final String CODE = "INVALID_EXPENSE_CANCELLATION";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidExpenseCancellationException(String detail) {
        super(CODE, detail);
    }
}
