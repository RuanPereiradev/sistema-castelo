package br.com.castel.billing.web;

import br.com.castel.billing.application.CashDrawerSessionWithTotals;
import br.com.castel.billing.domain.CashDrawerSession;
import br.com.castel.billing.domain.CashMovement;
import br.com.castel.sharedkernel.Money;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A cash drawer session as the front desk reads it. Money travels as decimal strings; what does not
 * exist yet — the closing, the count, the difference — is {@code null}.
 *
 * <p>An open session answers the live cash payments and expected amount; a closed one, the amounts
 * frozen at its closing. The closing is blind (decision C4 of task 2.4): while the session is open,
 * {@code cashPaymentsTotal} and {@code expectedAmount} are {@code null} for whoever is not an
 * {@code ADMIN}. {@code cashPaymentCount} is always the live count of confirmed cash payments.
 */
public record CashDrawerSessionResponse(
        String id,
        String status,
        Instant openedAt,
        String openedBy,
        String openingFloat,
        List<MovementResponse> movements,
        String totalDrops,
        String totalSupplies,
        String cashPaymentsTotal,
        long cashPaymentCount,
        String expectedAmount,
        String countedAmount,
        String difference,
        Instant closedAt,
        String closedBy,
        String closingNote) {

    public static CashDrawerSessionResponse from(CashDrawerSessionWithTotals reading, boolean viewerIsAdmin) {
        CashDrawerSession session = reading.session();
        Money livePayments = reading.cashPayments().total();
        boolean reveals = session.revealsExpectedAmountTo(viewerIsAdmin);
        Optional<Money> shownPayments = reveals
                ? Optional.of(session.frozenCashPayments().orElse(livePayments))
                : Optional.empty();
        Optional<Money> shownExpected = reveals ? Optional.of(session.expectedAmount(livePayments)) : Optional.empty();
        return new CashDrawerSessionResponse(
                session.id().value().toString(),
                session.status().name(),
                session.openedAt(),
                session.openedBy().toString(),
                session.openingFloat().asString(),
                session.movements().stream().map(MovementResponse::from).toList(),
                session.totalDrops().asString(),
                session.totalSupplies().asString(),
                textOf(shownPayments),
                reading.cashPayments().count(),
                textOf(shownExpected),
                textOf(session.countedAmount()),
                textOf(session.difference()),
                session.closedAt().orElse(null),
                session.closedBy().map(UUID::toString).orElse(null),
                session.closingNote().orElse(null));
    }

    public record MovementResponse(
            String id, String type, String amount, String reason, Instant createdAt, String createdBy) {

        static MovementResponse from(CashMovement movement) {
            return new MovementResponse(
                    movement.id().value().toString(),
                    movement.type().name(),
                    movement.amount().asString(),
                    movement.reason(),
                    movement.createdAt(),
                    movement.createdBy() == null ? null : movement.createdBy().toString());
        }
    }

    private static String textOf(Optional<Money> amount) {
        return amount.map(Money::asString).orElse(null);
    }
}
