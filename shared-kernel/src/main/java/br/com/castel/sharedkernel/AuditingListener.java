package br.com.castel.sharedkernel;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fills the audit columns of every {@link AuditedEntity} on insert and on update.
 *
 * <p>A plain JPA entity listener, declared by {@link AuditedEntity} and instantiated by the
 * persistence provider through the container's bean factory, which is what allows it to receive its
 * two collaborators by constructor instead of reaching for static state.
 *
 * <p>Spring Data JPA's own auditing is not used: its annotations ({@code @CreatedDate},
 * {@code @CreatedBy} and the rest) would have to sit on the fields of {@link AuditedEntity}, and
 * this module is not allowed to see Spring (decision #30).
 */
public class AuditingListener {

    private final AuditorAware auditorAware;
    private final Clock clock;

    public AuditingListener(AuditorAware auditorAware, Clock clock) {
        this.auditorAware = Objects.requireNonNull(auditorAware, "auditorAware");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @PrePersist
    void stampCreation(AuditedEntity entity) {
        entity.recordCreation(author(), now());
    }

    @PreUpdate
    void stampModification(AuditedEntity entity) {
        entity.recordModification(author(), now());
    }

    private UUID author() {
        return Objects.requireNonNull(
                auditorAware.currentAuditorId(), "currentAuditorId must never be null; an audited row needs an author");
    }

    private Instant now() {
        return clock.instant();
    }
}
