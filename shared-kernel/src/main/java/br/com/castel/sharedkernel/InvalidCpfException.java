package br.com.castel.sharedkernel;

import java.io.Serial;

/**
 * Thrown when a {@link Cpf} is malformed or fails check-digit validation.
 *
 * <p>The message never includes the submitted value, since a CPF is personal data.
 */
public final class InvalidCpfException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "INVALID_CPF";

    InvalidCpfException(String message) {
        super(CODE, message);
    }
}
