package br.com.castel.billing.domain;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.sharedkernel.Money;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.util.UUID;

/** A night of a stay, posted by the hotel module. Belongs only on a stay folio. */
@Entity
@DiscriminatorValue("ROOM_NIGHT")
public class RoomNightCharge extends Charge {

    protected RoomNightCharge() {
        // for JPA
    }

    private RoomNightCharge(Money amount, String description, UUID sourceId, String reason, ChargeId reversalOf) {
        super(amount, description, sourceId, null, reason, reversalOf);
    }

    /** Receives values the aggregate already validated. */
    static RoomNightCharge of(Money amount, String description, UUID sourceId) {
        return new RoomNightCharge(amount, description, sourceId, null, null);
    }

    @Override
    public ChargeType type() {
        return ChargeType.ROOM_NIGHT;
    }

    @Override
    RoomNightCharge reversal(String validReason) {
        return new RoomNightCharge(amount().negate(), description(), sourceId().orElseThrow(), validReason, id());
    }
}
