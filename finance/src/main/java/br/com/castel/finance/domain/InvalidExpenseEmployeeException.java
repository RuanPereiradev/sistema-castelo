package br.com.castel.finance.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** Só a folha de pagamento nomeia um funcionário. */
public class InvalidExpenseEmployeeException extends DomainException {

    public static final String CODE = "INVALID_EXPENSE_EMPLOYEE";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidExpenseEmployeeException(String detail) {
        super(CODE, detail);
    }
}
