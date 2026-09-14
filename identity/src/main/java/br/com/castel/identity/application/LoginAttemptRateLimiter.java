package br.com.castel.identity.application;

import java.util.function.Supplier;

/**
 * Port that throttles the password checks of the login flow, so an attacker can neither guess
 * passwords at will nor burn the server's CPU with BCrypt.
 *
 * <p>Two failure limits, both inside a sliding window: one per pair (client IP, username), which
 * leaves the other usernames of the same IP free, and a higher one per client IP, summing every
 * username, against password spraying. A third, independent protection caps how many password
 * checks one client IP runs at the same time, and how many of its attempts may wait for one. Only
 * failed logins count; a successful login neither counts nor resets anything, and a rejection by
 * the password check slots is not a failure either.
 *
 * <p>The username is compared the way the login looks the user up: normalized by
 * {@code User.normalizedUsername}. A well-formed username that belongs to no user counts like any
 * other. Every username that is not well formed, and so can belong to no user, shares a single
 * pair per client IP, so client text never grows the number of tracked pairs.
 */
public interface LoginAttemptRateLimiter {

    /**
     * Whether {@code clientIp} may not attempt to log in as {@code username} right now, because
     * the pair or the IP already reached its failure limit inside the window.
     */
    boolean isBlocked(String clientIp, String username);

    /** Counts one failed login against the pair ({@code clientIp}, {@code username}) and against {@code clientIp}. */
    void recordFailure(String clientIp, String username);

    /**
     * Runs {@code passwordCheck} while holding one of the concurrent password check slots of
     * {@code clientIp}, waiting for a free slot when all of them are taken. The slot is given back
     * when {@code passwordCheck} returns or throws. Records no failure of its own.
     *
     * @throws TooManyLoginAttemptsException with {@link TooManyLoginAttemptsException.Reason#WAITING_LIMIT_REACHED}
     *         at once if {@code clientIp} already has the maximum of attempts waiting;
     *         {@link TooManyLoginAttemptsException.Reason#PASSWORD_CHECK_SLOT_TIMEOUT} if no slot
     *         frees up before the configured timeout; or
     *         {@link TooManyLoginAttemptsException.Reason#PASSWORD_CHECK_SLOT_WAIT_INTERRUPTED} if
     *         the thread is interrupted while waiting, with its interrupt flag restored
     */
    <T> T runWithPasswordCheckSlot(String clientIp, Supplier<T> passwordCheck);
}
