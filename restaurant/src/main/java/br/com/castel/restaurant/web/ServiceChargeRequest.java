package br.com.castel.restaurant.web;

import jakarta.validation.constraints.NotNull;

/**
 * Body of the two service charge switches, of the tab and of one item: {@code applied} false takes
 * the charge off, true puts it back. Absent is 400: there is no default to guess.
 */
public class ServiceChargeRequest {

    @NotNull
    private Boolean applied;

    public Boolean getApplied() {
        return applied;
    }

    public void setApplied(Boolean applied) {
        this.applied = applied;
    }
}
