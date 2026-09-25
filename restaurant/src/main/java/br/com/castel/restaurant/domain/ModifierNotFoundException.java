package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.NotFoundException;
import java.io.Serial;

/** No modifier answers the given id, or the item does not offer the modifier being withdrawn. */
public class ModifierNotFoundException extends NotFoundException {

    public static final String CODE = "MODIFIER_NOT_FOUND";

    @Serial
    private static final long serialVersionUID = 1L;

    public ModifierNotFoundException(String detail) {
        super(CODE, detail);
    }
}
