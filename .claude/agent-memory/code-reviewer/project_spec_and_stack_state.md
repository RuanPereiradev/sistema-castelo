---
name: project-spec-and-stack-state
description: Where task specs live, decisions already approved in shared-kernel, and the known CLAUDE.md vs pom Spring Boot version mismatch
metadata:
  type: project
---

- Task specs live in `docs/task-<n>-<slug>.md`: Part A for production, Part B for tests. They are synced after decisions, so review against them, not against the original request.
- Decisions approved in task 0.2 (do not flag as problems): `Money` has no `Currency` field; `InvalidMoneyException` carries 3 codes (INVALID_MONEY, MONEY_SCALE_EXCEEDED, MONEY_OUT_OF_RANGE); `Percentage` stored as a fraction with scale 4 (NUMERIC(5,4)), allows >100%; `Quantity` 1..999; ids use UUID v7, `public_token` uses v4; `Cpf` does not override `toString` (PII).
- Known mismatch (2026-09-11): CLAUDE.md header says Spring Boot 3.3, root `pom.xml` uses 4.1.1. Confirm whether it has been fixed before citing it again.

**Why:** avoids re-litigating approved design and keeps the version mismatch visible for modules that do use Spring (Boot 4 changes starters/Jackson).
**How to apply:** check these before classifying spec deviations; re-verify the pom/CLAUDE.md version on each review.
