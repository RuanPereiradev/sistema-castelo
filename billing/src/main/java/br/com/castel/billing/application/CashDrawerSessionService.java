package br.com.castel.billing.application;

import br.com.castel.billing.domain.CashDrawerSession;
import br.com.castel.billing.domain.CashDrawerSessionAlreadyOpenException;
import br.com.castel.billing.domain.CashDrawerSessionId;
import br.com.castel.billing.domain.CashDrawerSessionNotFoundException;
import br.com.castel.billing.domain.CashDrawerSessionRepository;
import br.com.castel.billing.domain.CashPaymentTotals;
import br.com.castel.billing.domain.IdempotencyKeyReusedException;
import br.com.castel.billing.domain.Payment;
import br.com.castel.sharedkernel.AuditorAware;
import br.com.castel.sharedkernel.CurrentProperty;
import br.com.castel.sharedkernel.Money;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The use cases of the cash drawer at the front desk: opening a session, drops and supplies, closing
 * and reading (task 2.4).
 *
 * <p>Every write loads the session {@code FOR UPDATE} (invariant 25 of task 2.4). The closing sums the
 * cash payments only after taking that lock, so every payment that fell into the session before it
 * is counted. The operator comes from {@link AuditorAware} and the moment from the {@link Clock}.
 *
 * <p>The rules about the set of sessions — one open per property, an idempotency key held by one
 * session — are checked here to answer a readable code, and held by the database against a race.
 */
@Service
public class CashDrawerSessionService {

    private final CashDrawerSessionRepository sessions;
    private final CurrentProperty currentProperty;
    private final AuditorAware auditorAware;
    private final Clock clock;

    public CashDrawerSessionService(
            CashDrawerSessionRepository sessions,
            CurrentProperty currentProperty,
            AuditorAware auditorAware,
            Clock clock) {
        this.sessions = sessions;
        this.currentProperty = currentProperty;
        this.auditorAware = auditorAware;
        this.clock = clock;
    }

    /** @throws CashDrawerSessionAlreadyOpenException if the property already has an open session */
    @Transactional
    public CashDrawerSessionWithTotals open(Money openingFloat) {
        CashDrawerSession session = CashDrawerSession.open(
                currentProperty.id(), openingFloat, auditorAware.currentAuditorId(), clock.instant());
        rejectSecondOpenSession();
        return new CashDrawerSessionWithTotals(sessions.save(session), CashPaymentTotals.none());
    }

    /** @throws IdempotencyKeyReusedException if another session holds the key */
    @Transactional
    public CashDrawerSessionWithTotals drop(
            CashDrawerSessionId sessionId, Money amount, String reason, String idempotencyKey) {
        String key = Payment.requireValidIdempotencyKey(idempotencyKey);
        CashDrawerSession session = loadForUpdate(sessionId);
        rejectKeyHeldByAnother(key, session);
        session.drop(amount, reason, key);
        return saveReadable(session);
    }

    /** @throws IdempotencyKeyReusedException if another session holds the key */
    @Transactional
    public CashDrawerSessionWithTotals supply(
            CashDrawerSessionId sessionId, Money amount, String reason, String idempotencyKey) {
        String key = Payment.requireValidIdempotencyKey(idempotencyKey);
        CashDrawerSession session = loadForUpdate(sessionId);
        rejectKeyHeldByAnother(key, session);
        session.supply(amount, reason, key);
        return saveReadable(session);
    }

    /**
     * Closes the session with the count of the drawer.
     *
     * @param closedByAdmin whether the authenticated user is an {@code ADMIN}; the route knows it
     */
    @Transactional
    public CashDrawerSessionWithTotals close(
            CashDrawerSessionId sessionId, Money countedAmount, String note, boolean closedByAdmin) {
        CashDrawerSession session = loadForUpdate(sessionId);
        CashPaymentTotals cashPayments = sessions.sumConfirmedCashPayments(sessionId);
        session.close(
                countedAmount,
                cashPayments.total(),
                note,
                auditorAware.currentAuditorId(),
                closedByAdmin,
                clock.instant());
        return new CashDrawerSessionWithTotals(sessions.save(session), cashPayments);
    }

    /** @throws CashDrawerSessionNotFoundException if no session is open */
    @Transactional(readOnly = true)
    public CashDrawerSessionWithTotals current() {
        return sessions.findOpen(currentProperty.id())
                .map(this::withTotals)
                .orElseThrow(() -> new CashDrawerSessionNotFoundException("No cash drawer session is open"));
    }

    /** @throws CashDrawerSessionNotFoundException if the session does not exist */
    @Transactional(readOnly = true)
    public CashDrawerSessionWithTotals find(CashDrawerSessionId sessionId) {
        return withTotals(sessions.findById(sessionId).orElseThrow(() -> notFound(sessionId)));
    }

    // ------------------------------------------------------------------ internals

    private CashDrawerSession loadForUpdate(CashDrawerSessionId sessionId) {
        return sessions.findByIdForUpdate(sessionId).orElseThrow(() -> notFound(sessionId));
    }

    /**
     * Reads the totals before saving, inside the transaction (decision #18 of task 2.4, the pattern of
     * decision #27 of task 1.3). A movement that takes the expected amount or a total out of the range
     * of {@link Money} fails here with {@code MONEY_OUT_OF_RANGE} and rolls back, instead of committing
     * a session that no closing and no reading could answer any more.
     */
    private CashDrawerSessionWithTotals saveReadable(CashDrawerSession session) {
        CashPaymentTotals cashPayments = sessions.sumConfirmedCashPayments(session.id());
        session.expectedAmount(cashPayments.total());
        session.totalDrops();
        session.totalSupplies();
        return new CashDrawerSessionWithTotals(sessions.save(session), cashPayments);
    }

    private CashDrawerSessionWithTotals withTotals(CashDrawerSession session) {
        return new CashDrawerSessionWithTotals(session, sessions.sumConfirmedCashPayments(session.id()));
    }

    /** One open session per property (decision C1); the index holds it against a race. */
    private void rejectSecondOpenSession() {
        if (sessions.findOpen(currentProperty.id()).isPresent()) {
            throw new CashDrawerSessionAlreadyOpenException("The property already has an open cash drawer session");
        }
    }

    /** An idempotency key belongs to one session (decision C9); the same session decides a retry itself. */
    private void rejectKeyHeldByAnother(String key, CashDrawerSession session) {
        Optional<CashDrawerSessionId> holder = sessions.findSessionByMovementKey(key);
        if (holder.filter(other -> !other.equals(session.id())).isPresent()) {
            throw new IdempotencyKeyReusedException("The idempotency key was used on another cash drawer session");
        }
    }

    private static CashDrawerSessionNotFoundException notFound(CashDrawerSessionId sessionId) {
        return new CashDrawerSessionNotFoundException("No cash drawer session " + sessionId.value());
    }
}
