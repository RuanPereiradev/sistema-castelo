package br.com.castel.billing.web;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.billing.api.ChargeRequest;
import br.com.castel.billing.api.ChargeSource;
import br.com.castel.billing.api.FolioFacade;
import br.com.castel.billing.api.FolioId;
import br.com.castel.billing.api.FolioOwner;
import br.com.castel.billing.api.FolioReference;
import br.com.castel.billing.api.FolioType;
import br.com.castel.sharedkernel.Money;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opens a folio and posts on it in {@code dev}, so {@code http/40-billing-folios.http} can run
 * before the hotel and the restaurant call the facade (decision #12 of task 1.3). The owner is a new
 * UUID each time.
 *
 * <p>Restricted, like {@code DevUserSeeder}, to the {@code dev} profile and to
 * {@code castel.dev.seed-enabled=true}, and to {@code ADMIN}. It calls only the {@link FolioFacade},
 * which is exactly what the other modules will call.
 */
@RestController
@RequestMapping("/api/billing/dev/folios")
@Profile("dev")
@ConditionalOnProperty(name = "castel.dev.seed-enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
public class DevFolioController {

    private final FolioFacade folios;

    public DevFolioController(FolioFacade folios) {
        this.folios = folios;
    }

    /** A missing code or label reaches the domain as blank, which refuses it with its code. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DevFolioResponse openFolio(@Valid @RequestBody DevOpenFolioRequest request) {
        FolioId folioId = request.getType() == FolioType.STAY
                ? folios.openStayFolio(
                        FolioOwner.reservation(UUID.randomUUID()),
                        new FolioReference(
                                Objects.toString(request.getReferenceCode(), ""),
                                Objects.toString(request.getReferenceLabel(), "")))
                : folios.openTabFolio(FolioOwner.tab(UUID.randomUUID()));
        return DevFolioResponse.from(folios.findById(folioId), null);
    }

    @PostMapping("/{folioId}/charges")
    @ResponseStatus(HttpStatus.CREATED)
    public DevFolioResponse postCharge(
            @PathVariable("folioId") String folioId, @Valid @RequestBody DevPostChargeRequest request) {
        FolioId id = FolioId.of(folioId);
        ChargeId chargeId = folios.post(id, new ChargeRequest(
                Money.of(request.getAmount()),
                Objects.toString(request.getDescription(), ""),
                new ChargeSource(request.getSourceType(), UUID.randomUUID())));
        return DevFolioResponse.from(folios.findById(id), chargeId);
    }
}
