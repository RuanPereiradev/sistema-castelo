package br.com.castel.finance.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A descrição de uma despesa é obrigatória e cabe em 200 caracteres. */
public class InvalidExpenseDescriptionException extends DomainException {

    public static final String CODE = "INVALID_EXPENSE_DESCRIPTION";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidExpenseDescriptionException(String detail) {
        super(CODE, detail);
    }
}
