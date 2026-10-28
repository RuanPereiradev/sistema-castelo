package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The name the customer typed on the phone is missing, blank or past 40 characters once trimmed (decision #46 of task qr-table-payment). */
public class InvalidCustomerNameException extends DomainException {

    public static final String CODE = "INVALID_CUSTOMER_NAME";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCustomerNameException(String detail) {
        super(CODE, detail);
    }
}
