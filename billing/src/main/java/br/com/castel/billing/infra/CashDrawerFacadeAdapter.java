package br.com.castel.billing.infra;

import br.com.castel.billing.api.CashDrawerFacade;
import br.com.castel.billing.application.CashDrawerSessionService;
import br.com.castel.billing.domain.CashDrawerSessionId;
import br.com.castel.sharedkernel.Money;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Answers the {@link CashDrawerFacade} port over the use case the counter's own routes already use,
 * so there is one path to the drawer and not two.
 */
@Component
class CashDrawerFacadeAdapter implements CashDrawerFacade {

    private final CashDrawerSessionService sessions;

    CashDrawerFacadeAdapter(CashDrawerSessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public Optional<UUID> currentSessionId() {
        return sessions.currentIfOpen().map(session -> session.id().value());
    }

    @Override
    public void payExpense(UUID sessionId, Money amount, String reason, String idempotencyKey) {
        sessions.payExpense(new CashDrawerSessionId(sessionId), amount, reason, idempotencyKey);
    }
}
