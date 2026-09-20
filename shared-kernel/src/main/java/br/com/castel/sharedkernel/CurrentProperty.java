package br.com.castel.sharedkernel;

import java.time.ZoneId;
import java.util.UUID;

/**
 * The property this installation runs.
 *
 * <p>Every transactional table carries {@code property_id}, so every module that writes one needs
 * this value — and the composition root is the only place that knows it. Declared here, as plain
 * Java, because a domain module may not see {@code app}.
 *
 * <p>This is not multi-property infrastructure. Version 1 runs one property and proves it while the
 * context starts; when a second one exists, the id stops coming from here and starts coming from
 * the request, which is a change of this interface and of nothing else.
 */
public interface CurrentProperty {

    /**
     * @throws IllegalStateException if no property exists yet, which means the database was migrated
     *         but never seeded
     */
    UUID id();

    /**
     * The zone the clock on the wall of the property runs on.
     *
     * <p>A schedule of the domain — the hour a menu item starts being served, check-in and check-out
     * times — is local time. The server is normally in UTC, so reading its zone instead would move
     * every schedule by the offset, silently.
     *
     * @throws IllegalStateException if no property exists yet
     */
    ZoneId timeZone();
}
