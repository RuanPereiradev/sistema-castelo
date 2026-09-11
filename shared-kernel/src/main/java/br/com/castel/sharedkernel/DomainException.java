package br.com.castel.sharedkernel;

import java.io.Serial;
import java.util.Objects;

/**
 * Base type of every domain exception.
 *
 * <p>Each subclass passes an explicit constant as its code. The code is never derived
 * from the class name, so a rename cannot silently change the contract with the front end.
 */
public abstract class DomainException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    /** Stable code in UPPER_SNAKE_CASE, used by the HTTP response, the .http asserts and the UI translation. */
    public final String code() {
        return code;
    }
}
