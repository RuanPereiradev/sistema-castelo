---
name: task-3-6-tab-transfer-review
description: Task 3.6 (transfer/merge/move) round-1 review 2026-10-02: /move drops splitGroup and serviceChargeApplied like it dropped guestCount; lock order verified sound
metadata:
  type: project
---

Round 1 of `task/3.6-tab-transfer-merge` (2026-10-02). Decisions T1–T17 in
`docs/decisions/task-3.6.md`. Build green: 1819 tests, ArchUnit 16+18.

**Recurring pattern worth naming — "the new tab starts fresh".** `/move` is
`openForTable(newTable)` + `newTab.absorb(oldTab)`, so every *tab-level* piece of
operator state that is not explicitly carried over is silently reset. T17 fixed
`guestCount` after the test agent found it. Still reset in round 1:
`serviceChargeApplied` (a tab with the charge off becomes a tab with it on; the
items arrive waived so the money is right, but the next item ordered is charged)
and every item's `splitGroup` (T9's reset is unconditional in
`TabItem.transferTo`). `destination` is safe — only `close()` sets it.
**How to apply:** any future "open a new aggregate and absorb the old one"
implementation — ask for the full list of fields on the old root and which ones
travel. One fix for one field is not the fix.

**Verified sound, do not redo unless touched:**
- Two-tab lock order: `JpaTabRepository.lockBoth` sorts by `TabId::value` and
  every operation of the task uses it. No cycle exists: a merge takes exactly one
  `FOR UPDATE` + one `FOR KEY SHARE` in ascending order, `moveToTable` takes one
  lock, and the KDS path (`findByItemIdForItemChange`) never requests an
  exclusive *tab* lock, so its out-of-order KEY SHARE accumulation across retry
  attempts cannot close a cycle.
- `tab_id` has exactly one writer: `TabItem.tabId` is `@Column(nullable=false)`
  (insertable+updatable true) and `Tab.items`' `@JoinColumn` is
  `insertable=false, updatable=false`. The spec §2 still says
  `insertable = false` on the attribute, which would never write it — the **spec**
  is wrong, the code is right.
- T7 only ever turns the waiver ON (`if (waive) this.serviceChargeWaived = true`).
- V11 == schema doc §11.3 SQL byte for byte (awk the ```sql block + strip
  comments, diff).
- Flush order in `moveToTable`: `tabs.add(newTab)` is `saveAndFlush`, and the
  trail rows are written after, so their FKs have a target.
- `JpaKitchenQueue` calls `entityManager.flush()` before its plain-SQL read, so
  the `TRANSFERRED` ticket carries the new table.

**Found, recheck in round 2:** the two carry-over resets above; `FOLIO_BALANCE_NOT_ZERO`
has no block in `http/36` though spec §9 lists it; decision log left every row of
T1–T16 as `pendente` and the scope checklist unticked; the two new error codes are
absent from the "Contrato com o front" table; test:production ≈ 2.1:1 against the
1:1 budget; `guestCount` silently clamps at 999 on a merge, undocumented.
