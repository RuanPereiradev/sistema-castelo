package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The Pix receiver came without a key, a name or a city, or with a key longer than 77 characters (decision #33 of task qr-table-payment). */
public class InvalidPixReceiverException extends DomainException {

    public static final String CODE = "INVALID_PIX_RECEIVER";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidPixReceiverException(String detail) {
        super(CODE, detail);
    }
}
