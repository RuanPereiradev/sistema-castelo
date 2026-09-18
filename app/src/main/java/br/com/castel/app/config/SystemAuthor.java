package br.com.castel.app.config;

import java.util.UUID;

/**
 * The author recorded when a row is written with nobody authenticated: the development seed, a
 * migration, a scheduled job.
 *
 * <p>A reserved constant UUID, never {@code null} and never a magic string. All-zero but for the
 * final digit, so it stands out in a query, cannot collide with a version 7 UUID (whose version
 * nibble is never zero), and does not break a future foreign key to {@code app_user} any more than
 * a null would.
 */
public final class SystemAuthor {

    /** {@code 00000000-0000-0000-0000-000000000001}: the system itself wrote this row. */
    public static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private SystemAuthor() {
    }
}
