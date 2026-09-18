package br.com.castel.sharedkernel;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;

/**
 * The four audit columns every transactional table carries: who created the row and when, who last
 * changed it and when.
 *
 * <p>Inherited by every transactional entity of the system, so the columns are declared once instead
 * of being copied by hand into some twenty aggregates.
 *
 * <p>{@code created_at} and {@code created_by} are not updatable: once written, no later save can
 * rewrite who created the row, whatever the code does. {@code updated_at} and {@code updated_by}
 * change on every update, without exception. Nothing here is set by business code;
 * {@link AuditingListener} is the only writer, which is why the two recording methods are not
 * public.
 */
@MappedSuperclass
@EntityListeners(AuditingListener.class)
public abstract class AuditedEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    /**
     * Stamps the insert. The modification columns are filled too, so a row never carries "changed by
     * nobody, never": the first version of the row was itself a change.
     */
    void recordCreation(UUID authorId, Instant at) {
        this.createdAt = at;
        this.createdBy = authorId;
        this.updatedAt = at;
        this.updatedBy = authorId;
    }

    void recordModification(UUID authorId, Instant at) {
        this.updatedAt = at;
        this.updatedBy = authorId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public UUID updatedBy() {
        return updatedBy;
    }
}
