package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.Money;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.util.Optional;
import java.util.UUID;

/**
 * A correction written by an {@code ADMIN}, with a reason: positive adds to what the folio owes,
 * negative is a discount. It is how a balance that is not zero gets settled before closing
 * (decision #4 of task 1.3), and how a partial reversal is made (decision #7).
 *
 * <p>Never reversed: a wrong adjustment is corrected by another adjustment.
 *
 * <p>The idempotency key makes a retry after a lost answer write the adjustment once, as it does for
 * a {@link Payment} (task folio-adjustment-idempotency). It is unique across every folio
 * ({@code uk_charge_idempotency}), in a space of its own. An adjustment written before the key
 * existed has none.
 */
@Entity
@DiscriminatorValue("ADJUSTMENT")
public class AdjustmentCharge extends Charge {

    /** Maximum length of an idempotency key after trimming, matching the column. */
    public static final int MAXIMUM_IDEMPOTENCY_KEY_LENGTH = 100;

    @Column(name = "idempotency_key", length = MAXIMUM_IDEMPOTENCY_KEY_LENGTH, updatable = false)
    private String idempotencyKey;

    protected AdjustmentCharge() {
        // for JPA
    }

    private AdjustmentCharge(
            Money amount, String description, String reason, UUID authorizedBy, String idempotencyKey) {
        super(amount, description, null, authorizedBy, reason, null);
        this.idempotencyKey = idempotencyKey;
    }

    /** Receives values the aggregate already validated. */
    static AdjustmentCharge of(
            Money amount, String description, String reason, UUID authorizedBy, String idempotencyKey) {
        return new AdjustmentCharge(amount, description, reason, authorizedBy, idempotencyKey);
    }

    /**
     * The key trimmed, as it is compared and stored.
     *
     * @throws InvalidIdempotencyKeyException if the key is missing, blank or longer than 100
     *     characters once trimmed
     */
    public static String requireValidIdempotencyKey(String idempotencyKey) {
        return BoundedText.require(
                idempotencyKey, MAXIMUM_IDEMPOTENCY_KEY_LENGTH, "idempotency key", InvalidIdempotencyKeyException::new);
    }

    /**
     * Whether a retry with this body is the same adjustment as this one: the same amount, description
     * and reason, the texts compared trimmed, as they were stored.
     */
    public boolean matches(Money amount, String description, String reason) {
        return amount().equals(amount)
                && description().equals(trimmed(description))
                && reason().map(stored -> stored.equals(trimmed(reason))).orElse(false);
    }

    boolean hasIdempotencyKey(String trimmedKey) {
        return trimmedKey.equals(idempotencyKey);
    }

    /** The key it was written under; empty only for an adjustment older than the key. */
    public Optional<String> idempotencyKey() {
        return Optional.ofNullable(idempotencyKey);
    }

    @Override
    public ChargeType type() {
        return ChargeType.ADJUSTMENT;
    }

    @Override
    Charge reversal(String validReason) {
        throw new ChargeNotReversibleException("An adjustment is corrected by another adjustment, never reversed");
    }

    private static String trimmed(String text) {
        return text == null ? null : text.trim();
    }
}
