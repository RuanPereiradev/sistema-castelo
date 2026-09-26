package br.com.castel.billing.domain;

import br.com.castel.billing.api.ChargeId;
import br.com.castel.sharedkernel.AuditedEntity;
import br.com.castel.sharedkernel.Money;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.Optional;
import java.util.UUID;

/**
 * One line of a {@link Folio}: a room night, the total of a tab or an adjustment.
 *
 * <p>Append-only (invariant 14 of task 1.3): once written, a charge is never changed or removed. A
 * mistake is undone by a reversal, which is a charge of its own, of the same kind and source, with
 * the opposite amount and {@link #reversalOf()} naming the original (decision #7). The original
 * stays as it was.
 *
 * <p>Part of the {@link Folio} aggregate and created only through it: the rules that involve the
 * other charges, such as reversing a charge only once, live there. It does not map {@code folio_id},
 * which the owning side writes. The author is {@code created_by} of the audit columns (decision #9
 * of task 0.6).
 */
@Entity
@Table(name = "charge")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "charge_type", discriminatorType = DiscriminatorType.STRING, length = 20)
public abstract class Charge extends AuditedEntity {

    /** Maximum length of a description, matching the column. */
    public static final int MAXIMUM_DESCRIPTION_LENGTH = 255;

    /** Maximum length of the reason of an adjustment or a reversal. */
    public static final int MAXIMUM_REASON_LENGTH = 500;

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "amount", nullable = false)
    private Money amount;

    @Column(name = "description", nullable = false, length = MAXIMUM_DESCRIPTION_LENGTH)
    private String description;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "authorized_by")
    private UUID authorizedBy;

    @Column(name = "reason")
    private String reason;

    @Column(name = "reversal_of_charge_id")
    private UUID reversalOfChargeId;

    /**
     * The charge that reverses this one, when there is one. Not a column: the link is stored on the
     * reversal, and the {@link Folio} fills this side in before it hands a charge out.
     */
    @Transient
    private ChargeId reversedBy;

    protected Charge() {
        // for JPA
    }

    /** Receives values the aggregate already validated. */
    protected Charge(
            Money amount, String description, UUID sourceId, UUID authorizedBy, String reason, ChargeId reversalOf) {
        this.id = ChargeId.newId().value();
        this.amount = amount;
        this.description = description;
        this.sourceId = sourceId;
        this.authorizedBy = authorizedBy;
        this.reason = reason;
        this.reversalOfChargeId = reversalOf == null ? null : reversalOf.value();
    }

    /** The kind of this charge, which is also its discriminator. */
    public abstract ChargeType type();

    /**
     * The opposing charge: same kind, source and description, the opposite amount, pointing at this
     * one. Receives a reason the aggregate already validated.
     *
     * @throws ChargeNotReversibleException if this kind of charge cannot be reversed
     */
    abstract Charge reversal(String validReason);

    /** Records that {@code reversal} undoes this charge, for {@link #isReversible()} and reading. */
    void markReversedBy(ChargeId reversal) {
        this.reversedBy = reversal;
    }

    /**
     * Whether this charge can still be reversed: a room night or a tab charge that is not itself a
     * reversal and has not been reversed yet (decision #7 of task 1.3).
     */
    public boolean isReversible() {
        return type().isReversible() && !isReversal() && !isReversed();
    }

    /** Whether this line undoes another one. */
    public boolean isReversal() {
        return reversalOfChargeId != null;
    }

    /** Whether another line already undoes this one. */
    public boolean isReversed() {
        return reversedBy != null;
    }

    public ChargeId id() {
        return new ChargeId(id);
    }

    /** Positive for what the folio owes, negative for a discount or a reversal. */
    public Money amount() {
        return amount;
    }

    public String description() {
        return description;
    }

    /** The charge this one undoes. */
    public Optional<ChargeId> reversalOf() {
        return Optional.ofNullable(reversalOfChargeId).map(ChargeId::new);
    }

    /** The charge that undoes this one. */
    public Optional<ChargeId> reversedBy() {
        return Optional.ofNullable(reversedBy);
    }

    /** Why an adjustment or a reversal was written; empty for an ordinary posting. */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    /** The {@code ADMIN} who authorized an adjustment; empty for any other charge. */
    public Optional<UUID> authorizedBy() {
        return Optional.ofNullable(authorizedBy);
    }

    /** The id, in the module that posted it, of what this charge charges for; empty for an adjustment. */
    public Optional<UUID> sourceId() {
        return Optional.ofNullable(sourceId);
    }
}
