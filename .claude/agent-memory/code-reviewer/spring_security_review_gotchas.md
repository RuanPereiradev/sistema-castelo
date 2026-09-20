---
name: spring-security-review-gotchas
description: Boot 4.1.1 / Security 7 / Tomcat 11 / Jackson 3 / Postgres runtime behaviors confirmed in task 0.4 that recur in every module (error dispatch, XFF, log leaks, EPP registration, tx+pool+row lock, Unicode lower), plus probe recipes and test-run pitfalls
metadata:
  type: project
---

Confirmed by running the app (2026-09-13, Boot 4.1.1, Security 7.1.1, Tomcat 11.0.24, Jackson 3.1.5, jjwt 0.13.0).

**Security chain**
- The ERROR dispatch is authorized by default. Without `dispatcherTypeMatchers(ERROR).permitAll()`, 400 and 404 turn into 401.
- `forward-headers-strategy: framework` trusts XFF from anyone. `native` plus `server.tomcat.remoteip.internal-proxies` (regex or CIDR) only trusts listed proxies.
- `internal-proxies: ${VAR}` with VAR unset fails at boot with a cryptic `PatternSyntaxException`. An empty value trusts no proxy.
- A custom JWT `OncePerRequestFilter` needs `shouldNotFilter` for login/refresh. Exclude `UserDetailsServiceAutoConfiguration`.
- Problem bodies written by a filter or an entry point have no `instance`; `@RestControllerAdvice` ones do.
- **Boot 4 `EnvironmentPostProcessor`:**
  - The interface is now `org.springframework.boot.EnvironmentPostProcessor`; the old `boot.env` one is still in the jar.
  - The spring.factories key is the new FQN, the same one Boot's own jar uses.
  - It runs before the datasource and Tomcat, and fails in about 1s with no DB.

**Log leaks / parsing**
- `DefaultHandlerExceptionResolver` WARN logs Jackson's message. An unquoted `"password":secret` becomes "Unrecognized token 'secret'". A scoped `@ExceptionHandler(HttpMessageNotReadableException)` suppresses it.
- **Jackson echoes an unquoted token only while chars are `isJavaIdentifierPart`.** `Canary-abc` echoes `'Canary'` only. Leak canaries must be `[A-Za-z0-9_]`.
- BCrypt 7.1.1: `encode` throws IAE above 72 bytes, while `matches` returns false (after hashing, ~same time). jjwt `Keys.hmacShaKeyFor` rejects keys under 32 bytes, but 32 spaces pass.
- **Security 7 `matches("" or null, hash)` returns false WITHOUT hashing** (`AbstractValidatingPasswordEncoder`, `StringUtils.hasLength`). A dummy-hash timing defence is void for empty passwords; only DB work remains (~1.4ms extra for an existing user with an EAGER collection, 95% classifiable with 10 samples).
- **crypto 7.1.1 short-circuits, checked in the bytecode.** Use `javap -c -p -constants` on the jar in ~/.m2, which needs no build.
  - `matches` is final and checks only `StringUtils.hasLength` on both arguments. So " " and whitespace do get hashed.
  - `matchesNonNull` checks the regex `\A\$2(a|y|b)?\$(\d\d)\$[./0-9A-Za-z]{53}` on the stored hash only.
  - `checkpw` calls `hashpw(..., for_check=true)`, which skips the 72-byte IAE, so a long raw password still hashes.
  - A stored hash that matches the regex but has rounds outside 04-31 throws "Bad number of rounds".
- Jackson inputs for a String field: `null` or an absent field gives null. Numbers and booleans are coerced to non-empty strings. An object or array gives 400 before the service. No JSON value becomes "".
- `quote`-style log escaping verified: CR/LF/U+2028/bidi escaped, huge usernames truncated. Dev `show-sql` prints column name `password_hash` (no values), so grep for `$2a$`, not the column name.
- jjwt 0.13 rejects only when `now > exp` in ms, so a token at exactly `exp` is accepted.

**Transactions / pool / locks**
- `@Transactional` on a service method borrows a Hikari connection before the method body runs, so pool size caps concurrency ahead of any check inside it.
- **`SELECT ... FOR UPDATE` plus BCrypt in the same tx:**
  - Attempts on the same existing row serialize, while unknown users don't. That is a timing oracle.
  - N IPs x slots > pool (10) starves every other DB request; `/me` stalled 10s with 8 IPs.
- **Postgres `lower()` (en_US.utf8) differs from Java `toLowerCase(Locale.ROOT)`.** `lower('İ')='i'`, but Java gives `"i̇"`. Any in-memory key built from Java lowercase splits from a DB `lower()` match.

**Probe recipe**
1. `docker run -d --rm --name castel-review-pg -e POSTGRES_DB=castel_dev -e POSTGRES_USER=castel -e POSTGRES_PASSWORD=castel -p 55432:5432 postgres:16-alpine`
2. **Build the classpath.** Run `./mvnw -q -o dependency:build-classpath -pl app -Dmdep.outputFile=<scratch>/cp.txt`. Drop the `br/com/castel` jars (they are stale in ~/.m2) and prepend copies of `app`, `identity` and `shared-kernel` `target/classes`.
3. **Start each instance.** `java -Xmx400m -cp <cp> br.com.castel.app.CastelApplication --spring.profiles.active=dev --server.port=1808x --spring.datasource.url=...55432...` (about 30s to start).
4. **Isolate limiter buckets without a proxy.** Use `curl --interface 127.0.0.N`.
5. **Run `.http` files with ijhttp.**
   - `docker create --network host jetbrains/intellij-http-client --env-file http-client.env.json --private-env-file http-client.private.env.json --env dev -V host=... file.http`, then `docker cp dir/. cid:/workdir/`, then `docker start -a`.
   - Run `01` and `02` each on its own fresh instance.
6. **Extra users for concurrency probes.** Insert them via SQL, copying the seed user's `password_hash` and adding `user_role` rows.
7. **Timing, pool and thread probes.**
   - Timing oracle: 3 concurrent attempts (xargs -P3) on an existing vs an unknown user from one IP.
   - Pool: `/me` latency during a multi-IP wrong-password flood on one user.
   - Run these alone; a parallel mvn run adds noise.

Pitfalls:
- **`-Dsurefire.runOrder=random` does NOT shuffle `@Nested` classes or methods**; the order stays identical. Add `-Djunit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer$Random`, `-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random` and `-Djunit.jupiter.execution.order.random.seed=N`.
- **Huge surefire "Time elapsed" (for example 5424s) can be a laptop suspend.** Check `journalctl | grep Suspending` before calling it a hang.
- Don't `pkill -f` with a pattern that also appears in your own command.
- Subshell counters don't increment.

**How to apply:** test these at runtime in every module's review. Never touch the dev DB (5432).
