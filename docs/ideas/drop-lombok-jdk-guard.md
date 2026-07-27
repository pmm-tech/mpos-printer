# Drop Lombok from mpos-printer DTOs + fast-fail JDK guard

## Problem Statement
How might we stop `mvnw`/Lombok builds from silently breaking whenever the
system-default JDK drifts (as it just did, 21 -> 26), instead of just
patching this one occurrence?

## Recommended Direction
Remove Lombok entirely (only 2 small DTOs use it: `ReceiptData`,
`ReceiptLineData`) and replace `@Data`/`@AllArgsConstructor` with hand-written
getters/setters/constructors. This permanently removes the Lombok<->JDK
compatibility failure mode for this project, rather than chasing Lombok's
release cadence against future JDKs.

Pair it with a `maven-enforcer-plugin` `requireJavaVersion` rule pinned to
the supported range (e.g. `[21,22)`, matching the documented JDK 21
requirement). If the default JDK drifts again for any reason (not just
Lombok), the build fails immediately with one clear message instead of a
20-line wall of "cannot find symbol" errors.

## Context gathered during refinement
- No Lombok release supports JDK 26 yet (open GitHub issue #4019,
  unresolved as of July 2026). JDK 25 support landed in Lombok 1.18.40; this
  project is currently on 1.18.38.
- Only 2 files use Lombok: `ReceiptData.java` (4 fields, `@Data`) and
  `ReceiptLineData.java` (2 fields, `@Data @AllArgsConstructor`).
- 8 test call sites depend on `ReceiptLineData`'s Lombok-generated 2-arg
  constructor (`type, content` order) — must be preserved exactly in any
  hand-written replacement.
- No code relies on Lombok's generated `equals()`/`hashCode()`/`toString()`
  for either DTO — confirmed via grep across `src/test/java`.
- Confirmed directly: JDK 26.0.1's `javac` still accepts
  `-source 8 -target 8` today (warns "obsolete," doesn't reject it) — so
  removing Lombok fully fixes today's breakage. That target-8 setting is
  itself on borrowed time industry-wide, which is why the JDK guard matters
  as a second layer, not just a one-off fix.

## Key Assumptions to Validate
- [x] Full test suite still passes after hand-rolling the 2 DTOs (constructor
      param order for `ReceiptLineData(type, content)` must match exactly —
      verified 8 call sites depend on it) — 32/32 tests pass (30 existing +
      2 new characterization tests for `ReceiptLineData`)
- [x] No other code path relies on Lombok-generated `equals()`/`hashCode()`/
      `toString()` for these DTOs (checked: none found in tests)
- [x] Adding `maven-enforcer-plugin` doesn't conflict with anything in the
      existing build (it's build-time only, no runtime footprint — low risk)
      — confirmed: `mvn clean package` under JDK 21 still succeeds (32/32
      tests) with the plugin bound to the `validate` phase

## MVP Scope
- [x] Rewrite `ReceiptData.java` and `ReceiptLineData.java` as plain POJOs
- [x] Remove the `lombok` dependency from `pom.xml`
- [x] Add `maven-enforcer-plugin` with `requireJavaVersion` pinned to JDK 21
      (`[21,22)`, `maven-enforcer-plugin:3.6.3`, bound to `validate`)
- [x] Run `mvn clean test` under both JDK 21 and JDK 26 to confirm: JDK 21
      builds clean **and JDK 26 (the default) now also builds and tests clean
      with no `JAVA_HOME` override** — the Lombok removal alone already fixes
      today's breakage. The enforcer-plugin task remains, as a guard against
      *future* JDK drift (e.g. once `source`/`target 8` support is eventually
      dropped by a later `javac`), not because JDK 26 is broken today.

## Not Doing (and Why)
- Bumping `maven.compiler.source/target` off 8 — separate, bigger
  modernization call (touches the whole app's behavior); today's fix doesn't
  need it since JDK 26 still accepts target 8, just warns
- Re-adding Lombok later if future DTOs get complex (`@Builder`, custom
  `equals`, etc.) — that's a future decision on its own merits, not blocked
  by this change
- Pinning a machine-specific `toolchains.xml` — the enforcer-plugin approach
  is portable and doesn't hardcode absolute JDK paths

## Open Questions
- None blocking — ready to implement once reviewed and confirmed.
