package br.com.castel.billing.web;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.billing.api.FolioView;

/**
 * Answer of the {@code dev} routes, read from the {@link FolioView} of the facade. {@code chargeId}
 * is the charge just posted, and {@code null} on opening.
 */
public record DevFolioResponse(
        String id, String type, String status, String referenceCode, String balance, String chargeId) {

    static DevFolioResponse from(FolioView folio, ChargeId chargeId) {
        return new DevFolioResponse(
                folio.folioId().value().toString(),
                folio.type().name(),
                folio.status().name(),
                folio.reference().map(reference -> reference.code()).orElse(null),
                folio.balance().asString(),
                chargeId == null ? null : chargeId.value().toString());
    }
}
