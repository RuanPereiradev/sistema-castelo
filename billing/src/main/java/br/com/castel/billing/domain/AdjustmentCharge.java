package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.Money;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.util.UUID;

/**
 * A correction written by an {@code ADMIN}, with a reason: positive adds to what the folio owes,
 * negative is a discount. It is how a balance that is not zero gets settled before closing
 * (decision #4 of task 1.3), and how a partial reversal is made (decision #7).
 *
 * <p>Never reversed: a wrong adjustment is corrected by another adjustment.
 */
@Entity
@DiscriminatorValue("ADJUSTMENT")
public class AdjustmentCharge extends Charge {

    protected AdjustmentCharge() {
        // for JPA
    }

    private AdjustmentCharge(Money amount, String description, String reason, UUID authorizedBy) {
        super(amount, description, null, authorizedBy, reason, null);
    }

    /** Receives values the aggregate already validated. */
    static AdjustmentCharge of(Money amount, String description, String reason, UUID authorizedBy) {
        return new AdjustmentCharge(amount, description, reason, authorizedBy);
    }

    @Override
    public ChargeType type() {
        return ChargeType.ADJUSTMENT;
    }

    @Override
    Charge reversal(String validReason) {
        throw new ChargeNotReversibleException("An adjustment is corrected by another adjustment, never reversed");
    }
}
