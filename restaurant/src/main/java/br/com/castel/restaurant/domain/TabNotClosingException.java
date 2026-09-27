package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/**
 * The tab is not in the state this step of the closing needs: paid only once closing started,
 * reopened and closed only while {@code CLOSING}.
 */
public class TabNotClosingException extends ConflictException {

    public static final String CODE = "TAB_NOT_CLOSING";

    @Serial
    private static final long serialVersionUID = 1L;

    public TabNotClosingException(String detail) {
        super(CODE, detail);
    }
}
