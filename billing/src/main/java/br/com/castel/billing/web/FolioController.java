package br.com.castel.billing.web;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.application.FolioService;
import br.com.castel.billing.domain.PaymentId;
import br.com.castel.sharedkernel.Money;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The folio at the front desk (decision #3 of task 1.3): reading, receiving, reversing a charge and
 * closing are {@code ADMIN} and {@code FRONT_DESK}; an adjustment and the refund of a payment are
 * {@code ADMIN} only. {@code WAITER} and {@code KITCHEN} reach nothing here.
 *
 * <p>The class-level rule covers every route; the two {@code ADMIN} routes narrow it with their own
 * annotation, which takes precedence. Every {@code @PathVariable} names its variable explicitly.
 */
@RestController
@RequestMapping("/api/billing/folios")
@PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK')")
public class FolioController {

    /** Header of the key the front generates per payment attempt (decision #5 of task 1.3). */
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final FolioService folios;

    public FolioController(FolioService folios) {
        this.folios = folios;
    }

    @GetMapping("/{folioId}")
    public FolioResponse getFolio(@PathVariable("folioId") String folioId) {
        return FolioResponse.from(folios.find(FolioId.of(folioId)));
    }

    /** Only an open stay folio answers; a missing or unknown code is {@code FOLIO_NOT_FOUND}. */
    @GetMapping
    public FolioResponse findOpenStayFolio(
            @RequestParam(name = "referenceCode", required = false) String referenceCode) {
        return FolioResponse.from(folios.findOpenStayByReferenceCode(referenceCode));
    }

    /**
     * The key is read as an optional header on purpose (decision #19 of task 1.3): a missing required
     * header would fall into the catch-all as a 500, while the domain refuses it with its own code.
     */
    @PostMapping("/{folioId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceivedPaymentResponse receivePayment(
            @PathVariable("folioId") String folioId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        return ReceivedPaymentResponse.from(folios.receivePayment(
                FolioId.of(folioId), request.getMethod(), Money.of(request.getAmount()), idempotencyKey));
    }

    @PostMapping("/{folioId}/payments/{paymentId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public FolioResponse refundPayment(
            @PathVariable("folioId") String folioId,
            @PathVariable("paymentId") String paymentId,
            @RequestBody ReasonRequest request) {
        return FolioResponse.from(
                folios.refundPayment(FolioId.of(folioId), PaymentId.of(paymentId), request.getReason()));
    }

    @PostMapping("/{folioId}/adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public FolioResponse postAdjustment(
            @PathVariable("folioId") String folioId, @RequestBody AdjustmentRequest request) {
        return FolioResponse.from(folios.postAdjustment(
                FolioId.of(folioId), Money.of(request.getAmount()), request.getDescription(), request.getReason()));
    }

    @PostMapping("/{folioId}/charges/{chargeId}/reversal")
    @ResponseStatus(HttpStatus.CREATED)
    public FolioResponse reverseCharge(
            @PathVariable("folioId") String folioId,
            @PathVariable("chargeId") String chargeId,
            @RequestBody ReasonRequest request) {
        return FolioResponse.from(
                folios.reverseCharge(FolioId.of(folioId), ChargeId.of(chargeId), request.getReason()));
    }

    @PostMapping("/{folioId}/close")
    public FolioResponse closeFolio(@PathVariable("folioId") String folioId) {
        return FolioResponse.from(folios.closeFolio(FolioId.of(folioId)));
    }
}
