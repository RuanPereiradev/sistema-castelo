package br.com.castel.finance.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** Despesa em dinheiro sai da gaveta, e precisa de um turno de caixa aberto. */
public class CashExpenseRequiresDrawerSessionException extends ConflictException {

    public static final String CODE = "CASH_EXPENSE_REQUIRES_DRAWER_SESSION";

    @Serial
    private static final long serialVersionUID = 1L;

    public CashExpenseRequiresDrawerSessionException(String detail) {
        super(CODE, detail);
    }
}
