---
name: review-bigdecimal-untrusted-input
description: Recurring review check for value objects/DTOs receiving BigDecimal or numeric text from the API: exponent-based DoS, generic ArithmeticException leaks, tests that do not prove the guard
metadata:
  type: feedback
---

Whenever code builds a value object from a `BigDecimal` or numeric text that can come from JSON, probe it with jshell against `target/classes` (script in the session scratchpad, never in the repo) using: `1E+999999999`, `1E+10000000`, `1E+2147483648` (parses, scale = Integer.MIN_VALUE), `1E-2147483647`, `1E-10000000`, and `"1." + "0".repeat(200000)`.

Found in task 0.2 (shared-kernel, 2026-09-11), JDK 21.0.7:
- `toPlainString()` inside an exception message on a huge exponent -> OutOfMemoryError.
- `setScale(0, UNNECESSARY)` on `1E±10000000` -> ~4-20s; `1E±100000000` -> >60s. `1E±999999999` fails fast (BigInteger size guard), so testing only that value hides the stall.
- `Math.abs(scale)` guard is bypassed by scale `Integer.MIN_VALUE` -> generic ArithmeticException. Abs must be in `long`.
- `stripTrailingZeros()` is quadratic on long text with trailing zeros (200k chars ~19s).
- `new BigDecimal(String)` accepts Unicode digits (e.g. Arabic-Indic); a `[0-9]` regex does not.
- Second review: fixed via guard `|scale|>100 || precision>100` + 25-char text cap. But the tests only used values that a later cheap check also rejects (`1E+10000000` hits the `> max` compare; scale MAX_VALUE throws in `movePointLeft` and is caught). Removing the guard kept the suite green. Only a tiny positive value (`1E-10000000`) passes sign/range checks and reaches `setScale`.

- Final round: both halves of the guard now have a behavioural test (`1E-10000000` with timeout for scale; `"1" + "0".repeat(100)` → `INVALID_MONEY` instead of `MONEY_OUT_OF_RANGE` for precision).

**Accepted by the user, not deferred — do not raise again (2026-09-11):** tests must not try to prove *which* internal check rejected a value, and tests of the *order* of checks are not required where the codes are identical (`Weight`, `Percentage` use one code for guard and range). Proving the internal path is testing implementation, not behaviour. The behavioural contract is: rejected, with the right code, within the time limit, without echoing the value.

**Why:** the spec required guards "instead of stalling", and tests picked exponents that fail fast for unrelated reasons.
**How to apply:** for each guard, ask "which input reaches the expensive call if the guard is deleted?" and check a test uses that input *with a timeout* — that is behaviour. Verify by timing the unguarded expression in a separate jshell run. Time probes sequentially (parallel runs distort timings). Do not flag a missing test for check identity or check order when the observable code is the same.
