package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** A closing with a difference needs a note, and a note takes at most 500 characters (decision C6 of task 2.4). */
public class CashClosingNoteRequiredException extends DomainException {

    public static final String CODE = "CASH_CLOSING_NOTE_REQUIRED";

    @Serial
    private static final long serialVersionUID = 1L;

    public CashClosingNoteRequiredException(String detail) {
        super(CODE, detail);
    }
}
