package br.com.castel.billing.web;

import br.com.castel.billing.api.FolioReference;
import br.com.castel.billing.domain.Charge;
import br.com.castel.billing.domain.Folio;
import br.com.castel.billing.domain.Payment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A folio as the front desk reads it: header, charges, payments and the three totals. Money travels
 * as decimal strings; an absent reference, closing or link is {@code null}. {@code reversedBy} is
 * calculated on reading: the link is stored on the reversal.
 */
public record FolioResponse(
        String id,
        String type,
        String status,
        ReferenceResponse reference,
        Instant openedAt,
        Instant closedAt,
        List<ChargeResponse> charges,
        List<PaymentResponse> payments,
        String totalCharges,
        String totalPayments,
        String balance) {

    public static FolioResponse from(Folio folio) {
        return new FolioResponse(
                folio.id().value().toString(),
                folio.type().name(),
                folio.status().name(),
                folio.reference().map(ReferenceResponse::from).orElse(null),
                folio.openedAt(),
                folio.closedAt().orElse(null),
                folio.charges().stream().map(ChargeResponse::from).toList(),
                folio.payments().stream().map(PaymentResponse::from).toList(),
                folio.totalCharges().asString(),
                folio.totalPayments().asString(),
                folio.balance().asString());
    }

    public record ReferenceResponse(String code, String label) {

        static ReferenceResponse from(FolioReference reference) {
            return new ReferenceResponse(reference.code(), reference.label());
        }
    }

    public record ChargeResponse(
            String id,
            String type,
            String amount,
            String description,
            String reason,
            String reversalOf,
            String reversedBy,
            Instant createdAt,
            String createdBy) {

        static ChargeResponse from(Charge charge) {
            return new ChargeResponse(
                    charge.id().value().toString(),
                    charge.type().name(),
                    charge.amount().asString(),
                    charge.description(),
                    charge.reason().orElse(null),
                    charge.reversalOf().map(id -> id.value().toString()).orElse(null),
                    charge.reversedBy().map(id -> id.value().toString()).orElse(null),
                    charge.createdAt(),
                    textOf(charge.createdBy()));
        }
    }

    public record PaymentResponse(
            String id,
            String method,
            String amount,
            String status,
            Instant paidAt,
            String receivedBy,
            Instant refundedAt,
            String refundReason) {

        static PaymentResponse from(Payment payment) {
            return new PaymentResponse(
                    payment.id().value().toString(),
                    payment.method().name(),
                    payment.amount().asString(),
                    payment.status().name(),
                    payment.paidAt(),
                    payment.receivedBy().map(UUID::toString).orElse(null),
                    payment.refundedAt().orElse(null),
                    payment.refundReason().orElse(null));
        }
    }

    private static String textOf(UUID id) {
        return id == null ? null : id.toString();
    }
}
