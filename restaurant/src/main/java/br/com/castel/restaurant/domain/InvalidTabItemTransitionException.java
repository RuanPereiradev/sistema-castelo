package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** The status of the item does not accept the kitchen display transition asked for. */
public class InvalidTabItemTransitionException extends ConflictException {

    public static final String CODE = "INVALID_TAB_ITEM_TRANSITION";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidTabItemTransitionException(String detail) {
        super(CODE, detail);
    }
}
