package br.com.castel.billing.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link CashDrawerSession}. Implemented in {@code billing.infra}.
 *
 * <p>Two locks, on purpose (invariants 25 and 26 of task 2.4): the writes of the session itself take
 * {@code FOR UPDATE}; a payment that falls into the session takes {@code FOR KEY SHARE}. The two
 * conflict, so a closing waits for the cash payments already on their way and sums them after, and a
 * payment that arrives during a closing waits and then finds the session closed. Two payments do not
 * wait for each other.
 */
public interface CashDrawerSessionRepository {

    /** Reads a session without locking it. */
    Optional<CashDrawerSession> findById(CashDrawerSessionId id);

    /**
     * Reads a session and locks its row {@code FOR UPDATE} until the transaction ends. Every drop,
     * supply and closing goes through here before deciding anything.
     */
    Optional<CashDrawerSession> findByIdForUpdate(CashDrawerSessionId id);

    /** The open session of the property, if any, without locking it. */
    Optional<CashDrawerSession> findOpen(UUID propertyId);

    /**
     * Locks the open session of the property {@code FOR KEY SHARE} and answers its id; empty when no
     * session is open once the lock is taken. What every payment asks before falling into a session.
     */
    Optional<CashDrawerSessionId> findOpenForKeyShare(UUID propertyId);

    /**
     * Locks the session {@code FOR KEY SHARE}, open or closed: what the refund of a payment linked to
     * it takes, so the refund lands either before the sum of a closing or after the freezing.
     */
    void lockForKeyShare(CashDrawerSessionId id);

    /** The session holding a movement under this idempotency key, already trimmed. */
    Optional<CashDrawerSessionId> findSessionByMovementKey(String idempotencyKey);

    /** The confirmed {@code CASH} payments linked to the session. */
    CashPaymentTotals sumConfirmedCashPayments(CashDrawerSessionId id);

    /**
     * Writes the session and its new movements at once.
     *
     * @throws CashDrawerSessionAlreadyOpenException if another session of the property was opened first
     * @throws IdempotencyKeyReusedException if another session took the idempotency key first
     */
    CashDrawerSession save(CashDrawerSession session);
}
