package br.com.castel.billing.web;

import br.com.castel.billing.application.CashDrawerSessionService;
import br.com.castel.billing.domain.CashDrawerSessionId;
import br.com.castel.sharedkernel.Money;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The cash drawer at the front desk (decision C5 of task 2.4): every route is {@code ADMIN} and
 * {@code FRONT_DESK}; {@code WAITER} and {@code KITCHEN} reach nothing here. Who may close a given
 * session — whoever opened it, or an {@code ADMIN} — is a rule of the session, so the route only tells
 * it whether the user is an {@code ADMIN}. The same answer decides the blind closing of the response
 * (decision C4).
 *
 * <p>The idempotency key is read as an optional header, as for a payment (decision #19 of task 1.3):
 * the domain refuses its absence with its own code.
 */
@RestController
@RequestMapping("/api/billing/cash-sessions")
@PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK')")
public class CashDrawerSessionController {

    private static final String ADMIN_ROLE = "ADMIN";

    private final CashDrawerSessionService cashDrawerSessions;

    public CashDrawerSessionController(CashDrawerSessionService cashDrawerSessions) {
        this.cashDrawerSessions = cashDrawerSessions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CashDrawerSessionResponse openSession(
            @RequestBody OpenCashDrawerSessionRequest request, HttpServletRequest http) {
        return CashDrawerSessionResponse.from(
                cashDrawerSessions.open(Money.of(request.getOpeningFloat())), isAdmin(http));
    }

    @GetMapping("/current")
    public CashDrawerSessionResponse currentSession(HttpServletRequest http) {
        return CashDrawerSessionResponse.from(cashDrawerSessions.current(), isAdmin(http));
    }

    @GetMapping("/{sessionId}")
    public CashDrawerSessionResponse getSession(@PathVariable("sessionId") String sessionId, HttpServletRequest http) {
        return CashDrawerSessionResponse.from(
                cashDrawerSessions.find(CashDrawerSessionId.of(sessionId)), isAdmin(http));
    }

    @PostMapping("/{sessionId}/drops")
    @ResponseStatus(HttpStatus.CREATED)
    public CashDrawerSessionResponse registerDrop(
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(name = FolioController.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody CashMovementRequest request,
            HttpServletRequest http) {
        return CashDrawerSessionResponse.from(
                cashDrawerSessions.drop(
                        CashDrawerSessionId.of(sessionId),
                        Money.of(request.getAmount()),
                        request.getReason(),
                        idempotencyKey),
                isAdmin(http));
    }

    @PostMapping("/{sessionId}/supplies")
    @ResponseStatus(HttpStatus.CREATED)
    public CashDrawerSessionResponse registerSupply(
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(name = FolioController.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody CashMovementRequest request,
            HttpServletRequest http) {
        return CashDrawerSessionResponse.from(
                cashDrawerSessions.supply(
                        CashDrawerSessionId.of(sessionId),
                        Money.of(request.getAmount()),
                        request.getReason(),
                        idempotencyKey),
                isAdmin(http));
    }

    @PostMapping("/{sessionId}/close")
    public CashDrawerSessionResponse closeSession(
            @PathVariable("sessionId") String sessionId,
            @RequestBody CloseCashDrawerSessionRequest request,
            HttpServletRequest http) {
        boolean admin = isAdmin(http);
        return CashDrawerSessionResponse.from(
                cashDrawerSessions.close(
                        CashDrawerSessionId.of(sessionId),
                        Money.of(request.getCountedAmount()),
                        request.getNote(),
                        admin),
                admin);
    }

    /** Spring Security's request wrapper answers the role with its {@code ROLE_} prefix. */
    private static boolean isAdmin(HttpServletRequest http) {
        return http.isUserInRole(ADMIN_ROLE);
    }
}
