---
name: task-1-4-fake-ports-review
description: Task 1.4 (fake PaymentProcessor/TaxInvoiceIssuer) round-1 review 2026-09-25: no BLOQUEIA/CORRIGIR; accepted exceptions (#3 no .http, #8 app test imports infra); untested #6
metadata:
  type: project
---

Round 1 of `task/1.4-fake-ports` (2026-09-25, committed). Decisions #1-#8 in `docs/decisions/task-1.4.md`.
Build `clean verify -pl payment,tax-invoice,app -am` green; ArchUnit 16+18 green; `api/` untouched.

Accepted by decision (do not re-raise): no `.http` (#3, like 0.2/0.6); fakes on in every profile incl. prod with WARN (#1);
`,01` read from absolute value, so `-10.01` fails (#6); null args = NPE (#7); app composition test imports `infra` (#8).
`http/README.md` row was added by the orchestrator in the spec commit, not by DEV.

Open for next rounds: #6 negative case has no test; #5-#8 sit in "Decisões confirmadas" marked "aguarda Ruan"
(third task in a row with non-standard status values in the log, after 1.2 and 1.5).

**How to apply:** composition tests for ports prove wiring because a missing bean fails the autowire; they cannot
prove absence of `matchIfMissing`, so read the annotation instead.
