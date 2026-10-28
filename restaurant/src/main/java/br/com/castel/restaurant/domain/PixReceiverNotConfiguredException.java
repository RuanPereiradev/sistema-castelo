package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.ConflictException;
import java.io.Serial;

/** No Pix key, receiver name and city are configured, so the phone cannot pay (decision #15 of task qr-table-payment). */
public class PixReceiverNotConfiguredException extends ConflictException {

    public static final String CODE = "PIX_RECEIVER_NOT_CONFIGURED";

    @Serial
    private static final long serialVersionUID = 1L;

    public PixReceiverNotConfiguredException(String detail) {
        super(CODE, detail);
    }
}
