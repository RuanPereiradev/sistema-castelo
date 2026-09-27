package br.com.castel.billing.domain;

import br.com.castel.billing.api.PaymentMethod;
import java.util.Objects;
import java.util.Optional;

/**
 * Which cash drawer session a payment falls into, if any (decision C2 of task 2.4).
 *
 * <p>Built by the use case from the open session of the property and the setting
 * {@code billing.cash-drawer.required}, and handed to {@link Folio#receive}, which asks it only
 * after every rule of the payment itself:
 *
 * <ul>
 *   <li>a method that does not go to the drawer falls into no session, whatever the setting;
 *   <li>{@code CASH} with an open session falls into it;
 *   <li>{@code CASH} with no open session falls into none while cash control is off;
 *   <li>{@code CASH} with no open session is refused while cash control is on.
 * </ul>
 */
public final class CashDrawerAssignment {

    private static final CashDrawerAssignment UNCONTROLLED = new CashDrawerAssignment(Optional.empty(), false);

    private final Optional<CashDrawerSessionId> openSession;
    private final boolean required;

    private CashDrawerAssignment(Optional<CashDrawerSessionId> openSession, boolean required) {
        this.openSession = openSession;
        this.required = required;
    }

    /**
     * @param openSession the open session of the property, if there is one
     * @param required whether a cash payment needs an open session
     */
    public static CashDrawerAssignment of(Optional<CashDrawerSessionId> openSession, boolean required) {
        return new CashDrawerAssignment(Objects.requireNonNull(openSession, "openSession"), required);
    }

    /** No open session and cash control off: what a folio assumes when nobody tells it otherwise. */
    static CashDrawerAssignment uncontrolled() {
        return UNCONTROLLED;
    }

    /**
     * @throws CashDrawerSessionNotOpenException if the method goes to the drawer, there is no open
     *     session and cash control is on
     */
    public Optional<CashDrawerSessionId> sessionFor(PaymentMethod method) {
        Objects.requireNonNull(method, "method");
        if (!method.goesToCashDrawer()) {
            return Optional.empty();
        }
        if (openSession.isEmpty() && required) {
            throw new CashDrawerSessionNotOpenException("A cash payment needs an open cash drawer session");
        }
        return openSession;
    }
}
