package br.com.castel.sharedkernel;

import java.util.UUID;

/**
 * Who is writing right now.
 *
 * <p>Port implemented by the composition root over the authenticated user of the request. Declared
 * here, as plain Java, because {@link AuditingListener} is the one who asks, and this module knows
 * nothing about identity, HTTP or security.
 */
public interface AuditorAware {

    /**
     * The author to stamp on the row.
     *
     * <p>Never {@code null}: with nobody authenticated the implementation answers a reserved
     * constant for the system itself, so no audited row is ever left without an author.
     */
    UUID currentAuditorId();
}
