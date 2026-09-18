package br.com.castel.sharedkernel;

import java.net.URI;

/**
 * The value of {@code type} in every error body the API answers.
 *
 * <p>RFC 9457 lets {@code type} be absent and assumes {@code about:blank} then, and Spring leaves
 * it null by default, which drops the field from the JSON. Writing it explicitly keeps the shape of
 * an error body from varying: the front parses the same six fields every time.
 *
 * <p>Declared here because three modules write error bodies — the global handler of {@code app},
 * the exception handler of {@code identity} and its JWT filter — and a literal repeated in each one
 * is a contract waiting to drift.
 */
public final class ProblemType {

    /** The type of a problem that has no documentation page of its own, which is all of them. */
    public static final URI BLANK = URI.create("about:blank");

    private ProblemType() {
    }
}
