package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** Another modifier of the same property already carries this name. */
public class DuplicateModifierNameException extends ConflictException {

    public static final String CODE = "MODIFIER_NAME_ALREADY_USED";

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateModifierNameException(String detail) {
        super(CODE, detail);
    }
}
