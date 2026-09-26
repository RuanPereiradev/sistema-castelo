---
name: task-2-2-tab-review
description: Task 2.2 (Tab) round-1 review 2026-09-25: double cancel of one item races under FOR KEY SHARE; unfiltered list never exercised; locks and V7/doc verified
metadata:
  type: project
---

Round 1 of `task/2.2-tab` (2026-09-25, committed, worktree agent-a94e0bd8217e65d15). Decisions #1-#21 in `docs/decisions/task-2.2.md`. Build green, 1262 tests, ArchUnit 16+18.

Verified, no need to redo unless touched: V7 == schema doc section 11 SQL byte for byte (awk the ```sql block, diff); all section 5 codes have a `.http` negative; exception base classes match the HTTP table; no `@Version` anywhere; `MenuItem.isAvailableAt` refactor keeps semantics.

Found (recheck in round 2):
- Two concurrent `cancelItem` on the same item: both take `FOR KEY SHARE` on `tab`, both see PENDING, second UPDATE of `tab_item` overwrites author/reason/moment; both 200. Breaks invariant 17 under race. Fix idea: `FOR UPDATE` on the `tab_item` row before `findById` (does not touch #15).
- `GET /tabs` with no filter (both JPQL params null) never answered 200 in test or `.http`.
- Two-waiter concurrency test passes with any lock (or none); it proves "no false conflict", not `FOR KEY SHARE`.
- `TabItem.modifiers` bag has no `@OrderBy` (same as 1.2 menu modifiers).

Accepted by decision (do not re-raise): no test for cancel tab x add item race (moved to 3.2); `?cardNumber=abc` 500 (5.3); deactivate-table vs open race; unscoped property checks; 404 of menu item before TAB_NOT_OPEN (#21); missing modifier quantity = 0 -> 422 (#21); `if` in `DiningTableService.deactivate` (spec invariant 22).

**How to apply:** for any future "append-only + FOR KEY SHARE" aggregate, ask what happens when two requests mutate the *same child row*: KEY SHARE on the root does not serialize them.
