package br.com.castel.billing.domain;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.sharedkernel.Money;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.util.UUID;

/**
 * The total of a closed tab, posted by the restaurant module: one charge per tab, not one per item
 * (decision #3 of task 0.6). Accepted on the folio of a stay and on the folio of the tab itself.
 */
@Entity
@DiscriminatorValue("TAB")
public class TabCharge extends Charge {

    protected TabCharge() {
        // for JPA
    }

    private TabCharge(Money amount, String description, UUID sourceId, String reason, ChargeId reversalOf) {
        super(amount, description, sourceId, null, reason, reversalOf);
    }

    /** Receives values the aggregate already validated. */
    static TabCharge of(Money amount, String description, UUID sourceId) {
        return new TabCharge(amount, description, sourceId, null, null);
    }

    @Override
    public ChargeType type() {
        return ChargeType.TAB;
    }

    @Override
    TabCharge reversal(String validReason) {
        return new TabCharge(amount().negate(), description(), sourceId().orElseThrow(), validReason, id());
    }
}
