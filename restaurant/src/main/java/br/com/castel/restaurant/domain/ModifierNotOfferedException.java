package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The item does not offer the modifier ordered. */
public class ModifierNotOfferedException extends DomainException {

    public static final String CODE = "MODIFIER_NOT_OFFERED";

    @Serial
    private static final long serialVersionUID = 1L;

    public ModifierNotOfferedException(String detail) {
        super(CODE, detail);
    }
}
