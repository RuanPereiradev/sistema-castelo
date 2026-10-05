package br.com.castel.billing.api;

import br.com.castel.sharedkernel.Money;
import java.util.Optional;
import java.util.UUID;

/**
 * What another module may ask of the cash drawer.
 *
 * <p>Narrow on purpose: opening, closing and counting the drawer are the counter's own job and stay
 * behind billing's routes. What crosses the boundary is the one thing another module cannot do
 * without — taking cash out to pay an expense (decision D4 of task F1), so that the expected amount
 * drops with it and the blind closing still reconciles. Without that, the count would report a
 * shortfall for money the operator knowingly paid out, and a shortfall in the drawer looks like
 * theft.
 */
public interface CashDrawerFacade {

    /**
     * The open session of the property, if there is one. Whoever pays cash needs it: the money has
     * to leave a drawer that is open.
     */
    Optional<UUID> currentSessionId();

    /**
     * Registers cash leaving the drawer to pay an expense, in the caller's transaction: the expense
     * and the movement are stored together or not at all.
     *
     * @param sessionId the open session the money leaves
     * @param reason what was paid, which the operator reads on the drawer's statement
     * @param idempotencyKey a retry under the same key answers the movement already registered
     * @throws br.com.castel.billing.domain.CashDrawerSessionNotFoundException if there is no such session
     * @throws br.com.castel.billing.domain.CashDrawerSessionNotOpenException if the session is closed
     * @throws br.com.castel.billing.domain.IdempotencyKeyReusedException if another session holds the key
     */
    void payExpense(UUID sessionId, Money amount, String reason, String idempotencyKey);
}
