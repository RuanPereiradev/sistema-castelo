---
name: archunit-suite-pattern
description: How the 0.5a ArchUnit suite is structured (app module), the entity-scan gotcha it works around, and how to verify "does every rule have a real violation proof" quickly
metadata:
  type: project
---

Task 0.5a added `app/src/test/java/br/com/castel/app/architecture/`:
- `ArchitectureRules.java` — rule logic extracted into static methods/constants (package-private, final, private constructor), parameterized by base package where needed (module boundary rules A1/A2/A3), so the exact same method can run against real code and against synthetic fixtures.
- `ArchitectureRulesTest.java` — runs every rule against real production code (`ClassFileImporter` with `DO_NOT_INCLUDE_TESTS`, imports `br.com.castel`). This is the gate for `./mvnw clean install`.
- `ArchitectureViolationProofsTest.java` — runs the *same* rule methods/constants against fixtures under `architecture/violations/**`, asserting each one throws. One proof per rule, 1:1, no reimplementation.
- `architecture/violations/**` — deliberately-wrong fixtures, including a synthetic "fake module graph" (`violations/modulegraph/fake/fakehotel`, `fakebilling`, etc.) used to prove the module-boundary rules (A1/A2/A3/A4) without needing real cross-module violations.

**Isolation mechanism**: the real suite excludes fixtures via `DO_NOT_INCLUDE_TESTS` (fixtures live in `src/test/java`), not via package-name distance. The fake module names (`fakehotel`, etc.) nest under `br.com.castel.app...`, which is harmless specifically because of that import-option exclusion — don't assume naming alone provides isolation when reviewing future additions here.

**Real gotcha (verified by breaking it on purpose)**: `CastelApplication` uses `@SpringBootApplication(scanBasePackages = "br.com.castel")`, and `application-test.yml` sets `ddl-auto: validate`. Any `@Entity` placed under `br.com.castel.app.**` — even in `src/test/java` — gets picked up by Hibernate when any `@SpringBootTest` boots (e.g. `BaselineMigrationTest`), and validation fails unless it maps to columns that really exist. This is why the B1/D1 fixtures (`EntityWithPublicSetter`, `EntityInAppPackage`) deliberately map onto the real `property` table declaring only `id`. Confirmed by temporarily pointing one at a nonexistent table: `BaselineMigrationTest` broke immediately with a Hibernate schema-validation `ApplicationContext` failure. Don't flag this pattern as accidental scope creep — it's a documented, necessary workaround for a real side effect of `scanBasePackages` reaching test sources.

Similarly, `@RestController`/`@Component` fixtures that would otherwise become real beans are declared `abstract` (Spring's component scan skips abstract candidates), and the fake `...Repository` fixture deliberately does NOT extend `JpaRepository`/`CrudRepository`/`Repository` (Spring Data repository scan reaches the same package; a real Spring Data interface with no matching entity would try to build a broken proxy). Both were verified empirically to matter.

**Quick verification technique for "does this rule actually have a proof"**: temporarily weaken a rule in `ArchitectureRules.java` (or delete/rename a fixture file) and rerun `./mvnw test -pl app -Dtest=ArchitectureViolationProofsTest`; the corresponding proof test should fail with "Expecting code to raise a throwable." Always revert after.

`allowEmptyShould(true)` is used only on the 3 rules whose real-code "that()" universe is genuinely empty today (no `@Entity`, no concrete `EntityId`, no `@RestController` in production yet): B1, B3, C4. The module-boundary rules (A1-A4) don't need it because every domain module already has at least a `package-info.java` class, so their `that()` selection is never empty.

**Why:** this suite is easy to accidentally weaken (e.g. adding a rule without a proof, or a proof that reimplements rather than calls the same method) — future changes to this file should preserve the 1:1 rule-to-proof mapping and the isolation mechanism above.
**How to apply:** when reviewing changes to this suite, diff the rule list in `ArchitectureRules.java` against the `@Test` methods in both `ArchitectureRulesTest` and `ArchitectureViolationProofsTest` — every rule needs exactly one entry in each, calling the same method/constant.
