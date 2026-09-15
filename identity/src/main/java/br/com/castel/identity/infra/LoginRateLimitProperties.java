package br.com.castel.identity.infra;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Login throttling, bound from {@code castel.auth.login-rate-limit.*}.
 *
 * @param maxFailuresPerUsername failed logins allowed for one pair (client IP, username) inside
 *        {@code window}; the next attempt of that pair is rejected
 * @param maxFailuresPerIp failed logins allowed for one client IP inside {@code window}, summing
 *        every username; the next attempt of that IP is rejected, whatever the username
 * @param window sliding window the failures are counted in
 * @param maxTrackedPairs upper bound of pairs (client IP, username) kept in memory; beyond it
 *        Caffeine evicts by its size-based policy (W-TinyLFU)
 * @param maxTrackedIps upper bound of client IPs kept in memory, same eviction policy
 * @param maxConcurrentPasswordChecksPerIp password checks (BCrypt) one client IP may run at the
 *        same time; further attempts wait for a free slot
 * @param maxWaitingPasswordChecksPerIp attempts of one client IP allowed to wait for a slot at the
 *        same time; an attempt arriving beyond it is rejected at once, without waiting. Zero means
 *        no attempt ever waits
 * @param passwordCheckSlotTimeout how long an attempt waits for a free slot before it is rejected
 */
@ConfigurationProperties(prefix = "castel.auth.login-rate-limit")
public record LoginRateLimitProperties(
        int maxFailuresPerUsername,
        int maxFailuresPerIp,
        Duration window,
        long maxTrackedPairs,
        long maxTrackedIps,
        int maxConcurrentPasswordChecksPerIp,
        int maxWaitingPasswordChecksPerIp,
        Duration passwordCheckSlotTimeout) {

    private static final String PREFIX = "castel.auth.login-rate-limit.";

    public LoginRateLimitProperties {
        requireAtLeastOne(maxFailuresPerUsername, "max-failures-per-username");
        requireAtLeastOne(maxFailuresPerIp, "max-failures-per-ip");
        requirePositive(window, "window");
        requireAtLeastOne(maxTrackedPairs, "max-tracked-pairs");
        requireAtLeastOne(maxTrackedIps, "max-tracked-ips");
        requireAtLeastOne(maxConcurrentPasswordChecksPerIp, "max-concurrent-password-checks-per-ip");
        requireNotNegative(maxWaitingPasswordChecksPerIp, "max-waiting-password-checks-per-ip");
        requirePositive(passwordCheckSlotTimeout, "password-check-slot-timeout");
    }

    private static void requireAtLeastOne(long value, String property) {
        if (value < 1) {
            throw new IllegalArgumentException(PREFIX + property + " must be at least 1");
        }
    }

    private static void requireNotNegative(long value, String property) {
        if (value < 0) {
            throw new IllegalArgumentException(PREFIX + property + " must not be negative");
        }
    }

    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(PREFIX + property + " must be a positive duration");
        }
    }
}
