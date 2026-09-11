---
name: project-spec-and-stack-state
description: Where task specs live, decisions already approved in shared-kernel (do not re-flag), known accepted deviations, Spring Boot version state
metadata:
  type: project
---

- Task specs live in `docs/task-<n>-<slug>.md`: Part A for production, Part B for tests (written without reading the code). Specs are synced after each review, so review against them. Part A rules that Part B does not list tend to stay untested; check them.
- Decisions approved in task 0.2 (do not flag as problems): `Money` has no `Currency` field; `InvalidMoneyException` has 3 codes (INVALID_MONEY, MONEY_SCALE_EXCEEDED, MONEY_OUT_OF_RANGE); `Percentage` stored as a fraction with scale 4 (NUMERIC(5,4)), allows >100% up to 999.99%; `Quantity` 1..999; ids use UUID v7, `public_token` v4; `Cpf` does not override `toString` (PII); inclusive decimal guard 100/100 in `DecimalInput`, 25-char text cap after `strip()`; trailing zeros beyond scale accepted; `Weight` max 50,000 g; `DateRange` max 365 nights (stay only; RatePlan validity must not reuse it); exception messages never carry the rejected value.
- Known and accepted, not to reclassify (2026-09-11): `docs/schema.dbml` not synced with `schema-banco-de-dados.md` (user asked only for the `.md`); `Cpf.of` has no length cap (linear); `EntityId.of(String)` compiles the regex per call.
- Spring Boot version mismatch fixed on 2026-09-11: CLAUDE.md and root pom both say 4.1.1.

**Why:** avoids re-litigating approved design across reviews.
**How to apply:** check these before classifying spec deviations; re-verify the accepted items still hold (they may have been fixed or reopened).
