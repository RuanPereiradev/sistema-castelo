---
name: review-bigdecimal-untrusted-input
description: Recurring review check for value objects/DTOs receiving BigDecimal or numeric text from the API: exponent-based DoS, generic ArithmeticException leaks
metadata:
  type: feedback
---

Whenever code builds a value object from a `BigDecimal` or numeric text that can come from JSON, probe it with jshell against `target/classes` (no files needed) using: `1E+999999999`, `1E+10000000`, `1E+2147483648` (parses, scale = Integer.MIN_VALUE), `1E-2147483647`, and `"1." + "0".repeat(200000)`.

Found in task 0.2 (shared-kernel, 2026-09-11), JDK 21.0.7:
- `toPlainString()` inside an exception message on a huge exponent -> OutOfMemoryError (Money out-of-range message).
- `setScale(0)` after `movePointRight` on `1E+10000000` -> ~20s; `1E+100000000` -> >60s (Weight.ofKilos). `1E±999999999` fails fast (BigInteger size guard), so testing only that value hides the stall.
- `Math.abs(scale)` guard is bypassed by scale `Integer.MIN_VALUE` -> generic ArithmeticException (Money.multiply).
- `stripTrailingZeros()` is quadratic on long text with trailing zeros (200k chars ~19s).
- `new BigDecimal(String)` accepts Unicode digits (e.g. Arabic-Indic); a `[0-9]` regex does not.

**Why:** the spec required guards "instead of stalling", and the tests only used the one exponent that happens to fail fast.
**How to apply:** in every module review (restaurant weight/price DTOs, billing amounts, percentage settings), run these probes and time them sequentially (parallel jshell runs distort timings).
