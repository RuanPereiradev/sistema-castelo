package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The code of the customer's device is missing, or not 8 to 64 letters, digits, hyphens or underscores (decision #46 of task qr-table-payment). */
public class InvalidCustomerDeviceIdException extends DomainException {

    public static final String CODE = "INVALID_CUSTOMER_DEVICE_ID";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCustomerDeviceIdException(String detail) {
        super(CODE, detail);
    }
}
