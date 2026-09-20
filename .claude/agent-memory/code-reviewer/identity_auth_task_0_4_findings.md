---
name: identity-auth-task-0-4-findings
description: Task 0.4 (identity/JWT) review history rounds 1-6: decisions live in docs/decisions/task-0.4.md; round 6 (last) found empty-password timing oracle (Security 7 matches skips BCrypt); accepted limits not to re-raise
metadata:
  type: project
---

Rounds 1-4 (2026-09-12/13), round 5 (2026-09-13), round 6 "last round" (2026-09-14) on `task/0.4-identity-auth`.

**Source of truth: `docs/decisions/task-0.4.md`** (#1-#74, reverted ones kept, scope list, 3 items moved to 0.5b with approval, front contract, accepted limits). Read it first.

**Process rules from the owner:** #73: in the last round BLOQUEIA only for data loss or authentication failure (access, limiter bypass, credential leak, account enumeration by response or time); everything else is an "item a registrar". #74: from 0.5b on, max three review rounds per task.

**Accepted limits (never raise as findings):** up to 2 extra failures per layer under concurrency; `maxTrackedIps` untested; deliberate 1-minute lock of a pair; app tests importing identity.domain/infra (#70); IPv6 keyed by full address (#71, fix before prod); stale password accepted for ms during concurrent change (#61).

**Round 5 BLOQUEIAs, verified fixed in round 6:** row lock during BCrypt (now unlocked read, BCrypt outside tx, lock only on success; 3 concurrent existing/unknown/malformed all ~0.25s); `İ` pair split (now username validated `[a-z0-9._]{3,30}` after ROOT lowercase, malformed share a `<malformed>` sentinel pair). Kelvin sign U+212A is the only code point that lowercases into the ASCII set, and it lands on the same pair (verified). Waiting cap 3+30 verified (7 fast 429 of 40), timeouts/cap not counted. Two simultaneous correct logins leave exactly one valid token.

**Round 6 BLOQUEIA:** empty (or null) password. Spring Security 7 `AbstractValidatingPasswordEncoder.matches` returns false without hashing when the raw password is empty, so no BCrypt runs for existing, unknown or dummy. What remains is the DB read: existing user = query + EAGER `user_role` select. Measured with show-sql off: median 6.46ms vs 5.09ms; a 10-sample median classifier is 95.5% accurate, within the 10-per-pair limit. The `ObservingPasswordEncoder` counts `matches` calls before the delegate short-circuits, so "exactly one comparison" tests stay green.

**Restricted review of the empty-password fix (2026-09-14, static only, build owned by another agent):** fix verified with no BLOQUEIA. `login` swaps a null or empty password for a per-instance SecureRandom value (44 base64 chars), so all 4 account paths hash. The double now counts inside `matchesNonNull`, and its regex is the same as the lib's. Items to register:
- The HTTP relative-timing test (existing vs unknown within 2x) would also pass on the old code, since both paths took ms. It needs an absolute BCrypt floor.
- No audit-log test covers an empty password.
- The double counts before `checkpw`, which can still throw for rounds 00-03 or 32-99.
- R15 extensions: an empty stored hash `''`, and out-of-range rounds raising IAE (likely 500). Both are still unreachable.

The HTTP test file was edited mid-review by another agent.

**How to apply:** for any login path, probe with an empty password and a >72-byte password, not only a wrong one. For password-encoder test doubles, check whether they count the call or the actual hash work.
