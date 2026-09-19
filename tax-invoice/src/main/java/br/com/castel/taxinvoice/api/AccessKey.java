package br.com.castel.taxinvoice.api;

import java.util.Objects;

/**
 * What identifies an issued tax invoice at the tax authority.
 *
 * <p>Opaque on purpose: the format belongs to the document and to the issuer, and nothing in this
 * system reads inside it. Validating its shape would mean deciding which document the property
 * issues, which is not decided yet.
 */
public record AccessKey(String value) {

    public AccessKey {
        Objects.requireNonNull(value, "value");
    }
}
