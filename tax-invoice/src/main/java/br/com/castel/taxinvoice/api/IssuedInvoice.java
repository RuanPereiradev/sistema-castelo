package br.com.castel.taxinvoice.api;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A tax invoice that was issued.
 *
 * <p>{@code documentUrl} is where the payer reads it, when the issuer publishes one.
 */
public record IssuedInvoice(AccessKey accessKey, Instant issuedAt, Optional<URI> documentUrl) {

    public IssuedInvoice {
        Objects.requireNonNull(accessKey, "accessKey");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(documentUrl, "documentUrl");
    }
}
