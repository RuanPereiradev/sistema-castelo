package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** Another dining table of the same property already carries this label, ignoring letter case. */
public class DiningTableLabelAlreadyUsedException extends ConflictException {

    public static final String CODE = "DINING_TABLE_LABEL_ALREADY_USED";

    @Serial
    private static final long serialVersionUID = 1L;

    public DiningTableLabelAlreadyUsedException(String detail) {
        super(CODE, detail);
    }
}
