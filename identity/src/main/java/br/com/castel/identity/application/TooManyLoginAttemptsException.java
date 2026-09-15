package br.com.castel.identity.application;

import br.com.castel.sharedkernel.DomainException;
import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when a login attempt is throttled, always with the same code and message whatever the
 * {@link Reason}:
 *
 * <ul>
 *   <li>the pair (client IP, username) or the client IP already reached its failure limit inside
 *       the window, even when the credentials are correct;
 *   <li>the attempt waited for a password check slot of its IP longer than the configured timeout;
 *   <li>the IP already has the configured maximum of attempts waiting for a slot, so the attempt is
 *       rejected at once, without waiting;
 *   <li>the thread was interrupted while waiting for a slot.
 * </ul>
 *
 * <p>The {@link Reason} exists for the audit log only. It never reaches the HTTP response, so a
 * client cannot tell a throttled pair from a busy IP.
 */
public final class TooManyLoginAttemptsException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "TOO_MANY_LOGIN_ATTEMPTS";

    /** Why the attempt was throttled; written to the audit log, never to the response. */
    public enum Reason {
        FAILURE_LIMIT_REACHED,
        PASSWORD_CHECK_SLOT_TIMEOUT,
        WAITING_LIMIT_REACHED,
        PASSWORD_CHECK_SLOT_WAIT_INTERRUPTED
    }

    private final Reason reason;

    public TooManyLoginAttemptsException(Reason reason) {
        super(CODE, "Too many login attempts, try again later");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
