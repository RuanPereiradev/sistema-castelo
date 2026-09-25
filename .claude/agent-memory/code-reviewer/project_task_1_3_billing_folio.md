---
name: task-1-3-billing-folio-review
description: Task 1.3 (Folio) round-1 review 2026-09-25: lock bypassed by a stale persistence context (proven), probes via git-archive copy, set-rule translations untested, pre-existing identity flake
metadata:
  type: project
---

Round 1 of `task/1.3-billing-folio` (2026-09-25, committed branch). Decisions #1-#25 in `docs/decisions/task-1.3.md`.

**Probe method (the project must not be edited):** `git archive <branch> | tar -x -C <scratch>/probe`, then `./mvnw -q -pl app -am test -Dtest=X -Dsurefire.failIfNoSpecifiedTests=false` in the copy. Never `install`. Each run takes about 1-2 min.

**Findings:**
- `findByIdForUpdate` is a JPQL query with `@Lock`. If the caller's transaction already read the folio (for example `balanceOf`, then `close`, which is the natural 3.2 pattern), Hibernate locks the row but returns the managed instance with stale collections. Proven: the folio closed with balance 30.00.
  - `em.refresh(folio, PESSIMISTIC_WRITE)` crashes on Hibernate 7 with EAGER+SUBSELECT (NPE in EntityInitializer).
  - Detaching before the locking query works; all 5 billing ITs stay green.
- Removing `@Lock` makes both concurrency ITs fail 3/3. They do prove the lock. Without the lock, the same-key race returns 409, which shows the `uk_payment_idempotency` translation works.
- None of the set-rule paths have a committed test: FOLIO_ALREADY_OPENED_FOR_OWNER, REFERENCE_ALREADY_IN_USE, cross-folio key (service check or DB race), uk_charge_reversal. Yet the decision log's limitation claims "coberto".
- Writes that return a `Folio` build their response in the controller after commit. If `totalCharges` overflows `Money` (two 9.99e9 adjustments), the write commits, the response is a 422, and the folio is unreadable forever.
- `AuthenticationHttpIntegrationTest$LoginRateLimit.shouldReturnTooManyRequestsForNeverUsedUsername...` also fails on a `main` copy (2026-09-25), so it is pre-existing and not a 1.3 regression.

Accepted by decision (do not re-raise): `FOR NO KEY UPDATE` (#24), `IllegalStateException` for wrong owner (#23, spec text still says IAE), set-rule `if`s in `FolioService` (spec §4), optional `Idempotency-Key` header (#19), no FOLIO_ALREADY_OPENED_FOR_OWNER in `.http`.

**How to apply:** in rounds 2/3, rerun the stale-context probe first. For any future facade method meant for "the caller's transaction", check whether a prior read in that transaction defeats the lock.
