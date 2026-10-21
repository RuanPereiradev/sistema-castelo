package br.com.castel.restaurant.domain;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;

/** The request came without lines, with more than 20, or with an item sold by weight (decision #20). */
public class InvalidTableOrderRequestException extends DomainException {

    public static final String CODE = "INVALID_TABLE_ORDER_REQUEST";

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidTableOrderRequestException(String detail) {
        super(CODE, detail);
    }
}
