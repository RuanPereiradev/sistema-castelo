package br.com.castel.billing.domain;

import br.com.castel.sharedkernel.AuditedEntity;
import br.com.castel.sharedkernel.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A cash drop or a cash supply of a {@link CashDrawerSession}.
 *
 * <p>Append-only: a mistake is corrected by the opposite movement, with its reason. The author is
 * {@code created_by} of the audit columns. The idempotency key is what makes a double click register
 * the movement once (decision C9 of task 2.4); it is unique across every session.
 *
 * <p>Part of the {@link CashDrawerSession} aggregate and created only through it. It does not map
 * {@code cash_drawer_session_id}, which the owning side writes.
 */
@Entity
@Table(name = "cash_movement")
public class CashMovement extends AuditedEntity {

    /** Maximum length of the reason, after trimming. */
    public static final int MAXIMUM_REASON_LENGTH = 500;

    @Id
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private CashMovementType type;

    @Column(name = "amount", nullable = false)
    private Money amount;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "idempotency_key", nullable = false, length = Payment.MAXIMUM_IDEMPOTENCY_KEY_LENGTH)
    private String idempotencyKey;

    protected CashMovement() {
        // for JPA
    }

    private CashMovement(CashMovementType type, Money amount, String reason, String idempotencyKey) {
        this.id = CashMovementId.newId().value();
        this.type = type;
        this.amount = amount;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
    }

    /** Receives values the aggregate already validated. */
    static CashMovement of(CashMovementType type, Money amount, String reason, String idempotencyKey) {
        return new CashMovement(type, amount, reason, idempotencyKey);
    }

    /** Whether a retry of this type and amount is the same movement as this one. The reason does not count. */
    public boolean matches(CashMovementType type, Money amount) {
        return this.type == type && this.amount.equals(amount);
    }

    boolean hasIdempotencyKey(String trimmedKey) {
        return idempotencyKey.equals(trimmedKey);
    }

    boolean is(CashMovementType candidate) {
        return type == candidate;
    }

    public CashMovementId id() {
        return new CashMovementId(id);
    }

    public CashMovementType type() {
        return type;
    }

    /** Always greater than zero; the direction is in the {@linkplain #type() type}. */
    public Money amount() {
        return amount;
    }

    /** The amount as it moves the expected amount: negative for a drop. */
    public Money signedAmount() {
        return type.signedAmount(amount);
    }

    public String reason() {
        return reason;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
