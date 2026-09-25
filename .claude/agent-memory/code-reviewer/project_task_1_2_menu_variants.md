---
name: task-1-2-menu-variants-review
description: Task 1.2 (menu variants/modifiers) round-1 review 2026-09-24: no BLOQUEIA; recurring gaps (decision log missing template sections, #16 rules untested, element-collection re-offer untested against DB)
metadata:
  type: project
---

Round 1 mechanical review of `task/1.2-menu-variants-modifiers` (2026-09-24, uncommitted working tree). Build green, 1089 tests, ArchUnit 16+18 green. No BLOQUEIA.

Findings worth checking again next round:
- `docs/decisions/task-1.2.md` only had Estado + decisions table (#1-#23). Missing Escopo / Movido / Contrato com o front / Limitações sections that the template and task-0.8.md have; statuses stale (implemented rules still `pendente`, #10-#23 "aguarda Ruan").
- Decision #16 rules with no test: case-only rename of a variant (the `variant != renamed` filter in `MenuItem.rejectDuplicateVariantName`), trimmed name stored, re-offer of an already-offered modifier after it was deactivated.
- `@ElementCollection` `modifiers` re-offer = removeIf + add (Hibernate bag recreate: DELETE all + INSERT). Only `.http` #9 exercises it; the integration test offers each link once.
- Public-menu modifier order is undefined (no @OrderBy on the element collection).
- `offerModifier` never compares `modifier.propertyId()` with the item's property (same unscoped `findById` pattern as 0.8).
- Menu names (category, item, variant, modifier) are unique ignoring case at the DB too since V4 (`lower(name)` unique indexes, decision #27 of 1.2). A race on the same name still answers 500, not 409 — moved to task 5.3.

Accepted by decision (do not re-raise): first `org.hibernate` annotation outside shared-kernel (#17, SUBSELECT fetch); ids in `restaurant.domain` (#13); PATCH null-field `if`s in services (#20); variant uniqueness in the aggregate, modifier uniqueness in the use case (#15); `maxQuantity` absent = 400 VALIDATION_FAILED (#21).

**How to apply:** in round 2/3, check these first; round cap is 3 (0.4 #74).
