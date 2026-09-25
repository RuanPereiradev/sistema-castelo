---
name: task-1-5-dining-table-review
description: Task 1.5 (DiningTable) round-1 review 2026-09-25: natural-order comparator verified total/transitive; area tie-break splits case variants (diverges from #11); decision log stale again
metadata:
  type: project
---

Round 1 of `task/1.5-dining-table` (2026-09-25, uncommitted tree). Decisions #1-#16 in `docs/decisions/task-1.5.md`.

- `DiningTable.listingOrder()` / `compareNaturally`: probed with jshell (reflection on the private method, classes copied to scratchpad). 400 random strings, 200k triples: antisymmetric, total, transitive. Leading zeros, long digit runs, case fine.
- Found: the area key uses the same `compareNaturally`, whose final `compareTo` fallback splits "Varanda"/"varanda" into two adjacent blocks, so labels interleave across them. #11 says area ignores case. Accents: "Área" sorts after "Varanda" (compareToIgnoreCase, no Collator).
- `@PreAuthorize` class ADMIN + method override with `@P("includeInactive")` works; integration test covers WAITER 200 / 403 includeInactive / 403 create.
- Decision log repeated the 1.2 gap: scope checkboxes unchecked, Estado "Build —", #3/#4 still `pendente` though implemented.

Accepted by decision (do not re-raise): uniqueness check before the aggregate, so duplicate label + bad seats = 409 (#13); `if (label != null && exists...)` in service (1.2 pattern); `seats` stored as Short (#14); race on label = 500, moved to 5.3; unscoped `findById` (module-wide pattern since 0.8).

**How to apply:** rounds 2/3, recheck the area tie-break fix and log statuses first. For any future hand-written comparator, rerun the random triple probe.
