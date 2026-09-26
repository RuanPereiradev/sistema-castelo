package br.com.castel.billing.web;

import br.com.castel.billing.api.FolioType;
import jakarta.validation.constraints.NotNull;

/** Body of the {@code dev} route that opens a folio. The reference is for a {@code STAY} only. */
public class DevOpenFolioRequest {

    @NotNull
    private FolioType type;

    private String referenceCode;

    private String referenceLabel;

    public FolioType getType() {
        return type;
    }

    public void setType(FolioType type) {
        this.type = type;
    }

    public String getReferenceCode() {
        return referenceCode;
    }

    public void setReferenceCode(String referenceCode) {
        this.referenceCode = referenceCode;
    }

    public String getReferenceLabel() {
        return referenceLabel;
    }

    public void setReferenceLabel(String referenceLabel) {
        this.referenceLabel = referenceLabel;
    }
}
