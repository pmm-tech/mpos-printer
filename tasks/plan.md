# Implementation Plan: ESC/P Raw Printing for EPSON LX-300 Continuous Form

## Overview

`mpos-printer` currently prints exclusively through `javax.print`/AWT `Graphics2D` (`ReceiptWorker` → `PosReceipt` → `ReceiptPaper`), a model built around a single OS-driver printer and a page whose height is computed per job from content length. This doesn't fit an EPSON LX-300 dot-matrix printer on continuous-form paper: dot-matrix graphics rasterization through a driver is slow, "page height" isn't meaningful on tractor-feed paper, and barcodes rendered at 9-pin resolution on carbon-copy forms aren't useful anyway. The approved direction (see `docs/ideas/lx300-continuous-form-printing.md`) is a second, raw-ESC/P printing backend that streams text + control bytes directly to a raw OS print queue, selected per job, living alongside the existing backend untouched.

## Architecture Decisions

- **Routing**: new backward-compatible JSON wrapper `{ "printer": "escp", "formFeed": bool, "data": [...] }` added to the currently-dead `ReceiptData` DTO. Existing bare-array and `#`-delimited messages are completely unchanged and keep defaulting to the current printer. Same WebSocket port.
- **Config**: second profile lives in the same `printer.properties` file via prefixed keys (`escp.*`), same shared `PropertiesConfiguration` object — no second file/path-resolution system.
- **Durability**: in-memory bounded queue with retry + loud alert logging, explicitly NOT disk-backed. Deliberate scope cap, not an oversight.
- **MVP scope**: ESC/P backend is text-only (no barcode/image); no `Paper`/page-height math; form-feed byte emitted only when a job's `formFeed` flag is true; must work on both Windows (RAW-datatype queue) and Linux (CUPS raw queue); no `PrinterGuiApp` UI changes.
- **Fail-loud lookup**: `Helper.findPrinterByName`'s silent fallback-to-default-printer pattern is NOT reused for the escp path — a missing/misconfigured raw queue must error loudly, never silently substitute another printer.

## Task List

### Phase A: Foundation & risk reduction

- [x] Task A1: Test scaffolding (JUnit4/5 + Mockito)
- [~] Task A2: Raw-queue transport spike (Windows + Linux) — code + unit tests done; hardware validation blocked, see notes

### Checkpoint: Foundation
- [x] `mvn test` runs with the new test stack alongside existing `AppTest`
- [ ] Transport spike proves byte-for-byte fidelity on both OSes — **OPEN: blocked by lack of Windows/Linux/physical-LX-300 access from this sandbox; explicit gate before Phase C ships to production**

### Phase B: Message routing (backward compatible)

- [ ] Task B1: Extend `ReceiptData` DTO with `printer`/`formFeed`
- [ ] Task B2: `PrinterBackend` interface + `Graphics2DPrinterBackend` (zero behavior change)
- [ ] Task B3: `PrintServer` routing → stub `EscpPrinterBackend`

### Checkpoint 1: Routing
- [ ] All three message shapes (bare array, `#`-string, new escp wrapper) route correctly
- [ ] Legacy shapes produce byte-for-byte identical printed output to pre-change behavior
- [ ] escp wrapper only logs so far — no printer touched yet

### Phase C: Real ESC/P text output

- [ ] Task C1: ESC/P command byte builder (pure, unit-tested)
- [ ] Task C2: `escp.*` config keys + `EscpConfig` accessor
- [ ] Task C3: Wire `EscpPrinterBackend` to real bytes + transport

### Checkpoint 2: Core value delivery
- [ ] A real ESC/P job sent over the WebSocket prints correctly (text only) on the LX-300, on both OSes, synchronously

### Phase D: Form-feed control

- [ ] Task D1: Wire the real `formFeed` flag end-to-end

### Checkpoint 3: Continuous-form model validated
- [ ] Multi-part-form jobs (form-feed per document) and journal jobs (never) both physically validated

### Phase E: Durability / retry queue

- [ ] Task E1: In-memory bounded queue + background consumer (happy path)
- [ ] Task E2: Retry + backoff + cap + loud alert logging

### Checkpoint 4: Reliability
- [ ] Printer-offline scenario fails loudly, not silently, without becoming a disk-persistence project

### Phase F: Polish (optional, non-gating)

- [ ] Task F1: Sample config + deployment notes

### Checkpoint: Complete
- [ ] All acceptance criteria met across A–E
- [ ] Ready for review

## Task Details

### Task A1: Test scaffolding — DONE
**Description:** Add `junit` (4 or 5) and `mockito-core` as test-scope deps to `pom.xml`. Existing JUnit 3.8.1 `AppTest` stays as-is; all new tests use the modern stack.
**Acceptance criteria:**
- [x] `mvn test` runs a trivial new JUnit4/5 test successfully alongside existing `AppTest`
- [x] Mockito is usable (a throwaway test mocking an interface compiles/runs)
**Verification:** `mvn -q test` output shows the new test executed.
**Dependencies:** None.
**Files:** `pom.xml`.
**Estimated scope:** XS (1 file).

**Implementation notes:**
- The `junit` dependency was *upgraded in place* from 3.8.1 to 4.13.2 rather than adding a second `junit` artifact — JUnit 4's jar still bundles the legacy `junit.framework` package, so the existing JUnit-3-style `AppTest` keeps working unchanged (verified). Avoids two conflicting `junit` versions on the test classpath.
- Added `mockito-core:4.11.0` (last Mockito 4.x release, JDK 8-compatible — the project compiles with `-source/-target 8`).
- Added `src/test/java/id/modefashion/printer/ScaffoldingSpikeTest.java` to prove JUnit4 `@Test` + Mockito `mock()/when()/verify()` work together. Kept as a real (not deleted) minimal regression check of the test toolchain itself.
- **Unplanned but required fix, done first:** the build didn't compile at all on this machine before any of this — Lombok 1.18.38 silently fails to generate `@Data` methods on JDK 26 (this machine's default `java`), so `ReceiptWorker`/`PosReceipt` failed with "cannot find symbol: getType()/getContent()". Confirmed pre-existing on `master` (unrelated to this task) by stashing and rebuilding clean. Fixed by pinning the build to JDK 21 (`.java-version` + documented in `CLAUDE.md`), not by adding `--add-opens` flags (tested and confirmed unnecessary on JDK 21; would also break real JDK 8 builds, so intentionally not added).

### Task A2: Raw-queue transport spike (Windows + Linux) — CODE DONE, HARDWARE VALIDATION BLOCKED (see notes)
**Description:** Determine and validate the mechanism for writing raw, untranslated bytes to the LX-300 through the OS print queue on both target OSes, before any ESC/P rendering logic is built on top. Primary approach: `javax.print` `DocFlavor.BYTE_ARRAY.AUTOSENSE` against a queue configured OS-side as raw passthrough — a CUPS raw queue on Linux, a Generic/Text-Only driver on a RAW-datatype port on Windows. Build `RawPrintTransport` interface + `JavaxRawPrintTransport` impl, plus a throwaway manual harness (not part of the automated suite). Must fail loud (throw/log-and-abort) if the named queue isn't found — no fallback to system default. Only build a platform-specific fallback (e.g. JNA for WinSpool) if the primary approach fails byte-fidelity validation.
**Acceptance criteria:**
- [ ] A named raw queue can be located and an arbitrary `byte[]` (text + trailing form-feed byte) sent end-to-end on Linux with zero byte mangling (verify via captured `file://` CUPS backend output or raw device output) matching physical LX-300 printout — **NOT DONE, see notes**
- [ ] Same validated on Windows against a RAW-datatype/Generic-Text queue — **NOT DONE, see notes**
- [x] Missing/misconfigured queue name produces a clear, actionable logged error — never a silent substitute printer
**Verification:** Manual/hardware — not unit-testable. Where physical hardware isn't available, verify byte fidelity against a captured-bytes CUPS `file:` queue / Windows FILE: port, and flag physical-printer validation as a follow-up gate before Phase C ships.
**Dependencies:** None.
**Files:**
- `src/main/java/id/modefashion/printer/transport/RawPrintTransport.java`
- `src/main/java/id/modefashion/printer/transport/PrintServiceResolver.java`
- `src/main/java/id/modefashion/printer/transport/SystemPrintServiceResolver.java`
- `src/main/java/id/modefashion/printer/transport/JavaxRawPrintTransport.java`
- `src/main/java/id/modefashion/printer/transport/RawPrintTransportManualHarness.java`
- `src/main/java/id/modefashion/printer/transport/PrintTransportException.java`
- `src/test/java/id/modefashion/printer/transport/JavaxRawPrintTransportTest.java`
**Estimated scope:** S/M code; budget real time for physical/queue validation — this is the task most likely to surface a hard blocker.

**Implementation notes:**
- Built `JavaxRawPrintTransport` with a `PrintServiceResolver` seam (real impl: `SystemPrintServiceResolver`, matches exact queue name via `PrintServiceLookup`, deliberately does **not** fall back to the system default the way `Helper.findPrinterByName` does). This makes the fail-loud path and the byte-exact write path both genuinely unit-testable via a mocked `PrintServiceResolver`/`PrintService`/`DocPrintJob` — no live printer needed for that part. Three tests: queue-not-found throws (and never touches a `PrintService`), bytes handed to `job.print(...)` are byte-identical to the input, and a `PrintException` from the OS layer is wrapped (not swallowed).
- **Real-queue byte-fidelity validation (Linux/Windows/physical LX-300) is explicitly NOT done and remains an open gate before Phase C ships**, per the plan's own fallback language above. Attempted the local-CUPS-as-Linux-proxy approach (this Mac's CUPS is the same stack Linux uses): bind a loopback `nc` listener, add a temporary `socket://127.0.0.1:19100` raw CUPS queue via `lpadmin`, send bytes through `RawPrintTransportManualHarness`, byte-compare. Blocked structurally, not by choice: the sandbox's `sudo` has no TTY to accept a password non-interactively, so `lpadmin` (which requires root) can't run here at all. No queue was actually created (the failed call errored out before creating anything — confirmed via `lpstat`). This was a deliberate, user-approved attempt (two separate permission grants: the loopback listener, then `sudo`), not a skipped step.
- **Before this can be considered safe to ship**, someone needs to run `RawPrintTransportManualHarness <queueName>` against: (1) a real Linux box with a CUPS raw queue, (2) a real Windows box with a RAW-datatype/Generic-Text queue, (3) ideally the physical LX-300 itself — and confirm byte-for-byte fidelity each time (no line-ending translation, no injected headers).

### Task B1: Extend `ReceiptData` DTO
**Description:** Add `private String printer;` and `private boolean formFeed;` to `dto/ReceiptData.java` (Lombok `@Data` generates accessors).
**Acceptance criteria:**
- [ ] `ReceiptData` has `printer`/`formFeed` fields with Lombok-generated getters/setters
- [ ] Unit test asserts correct Gson deserialization of `{"printer":"escp","formFeed":true,"data":[...]}`, including `printer == null` when absent
**Verification:** `mvn test` — new `ReceiptDataTest`.
**Dependencies:** A1.
**Files:**
- `src/main/java/id/modefashion/printer/dto/ReceiptData.java`
- `src/test/java/id/modefashion/printer/dto/ReceiptDataTest.java`
**Estimated scope:** XS (2 files).

### Task B2: `PrinterBackend` interface + `Graphics2DPrinterBackend`
**Description:** Introduce a minimal `PrinterBackend` interface. `Graphics2DPrinterBackend` wraps today's two entry points unchanged: delegates to `new ReceiptWorker(data, config).proceed()` and `new ReceiptWorkerString(dataString, config).proceed()`, with zero changes to `ReceiptWorker`, `ReceiptWorkerString`, `PosReceipt`, `PosReceiptString`, or `ReceiptPaper`.
**Acceptance criteria:**
- [ ] `Graphics2DPrinterBackend` compiles and calls through to existing workers with identical arguments/order
- [ ] No changes to any file under `worker/` or `paper/`
**Verification:** Manual smoke test — send a pre-existing bare-array message and a pre-existing `#`-delimited message through the new wrapper and confirm identical printed output to before the change.
**Dependencies:** None.
**Files:**
- `src/main/java/id/modefashion/printer/backend/PrinterBackend.java`
- `src/main/java/id/modefashion/printer/backend/Graphics2DPrinterBackend.java`
**Estimated scope:** S (2 files).

### Task B3: `PrintServer` routing → stub `EscpPrinterBackend`
**Description:** Extract dispatch out of `PrintServer.onMessage` into a testable `JobRouter`. Routing order: (1) message starts with `{` and contains `"printer"` → parse as `ReceiptData` wrapper; `printer == "escp"` (case-insensitive) → `EscpPrinterBackend` (stub: logs job line count + `formFeed`, does not print); wrapper without/other `printer` → `Graphics2DPrinterBackend` with wrapper's data. (2) Else existing `message.contains("type")` check, byte-for-byte preserved → bare array → Graphics2D. (3) Else `#`-delimited string → Graphics2D. `PrintServer.onMessage` becomes a thin call into `JobRouter.route(message, config)`.
**Acceptance criteria:**
- [ ] `JobRouterTest` (unit, no live WebSocket) asserts all four routing branches
- [ ] Live WebSocket: pre-existing bare-array and `#`-string messages produce identical printed output to pre-change behavior
- [ ] Live WebSocket: new escp wrapper message produces a stub log line, no print attempt/error
**Verification:** Unit test (`JobRouterTest`); manual WebSocket client message + log inspection for live end-to-end + regression check.
**Dependencies:** B1, B2.
**Files:**
- `src/main/java/id/modefashion/printer/PrintServer.java`
- `src/main/java/id/modefashion/printer/backend/JobRouter.java`
- `src/main/java/id/modefashion/printer/backend/EscpPrinterBackend.java` (stub)
- `src/test/java/id/modefashion/printer/backend/JobRouterTest.java`
**Estimated scope:** S/M (4 files).

### Task C1: ESC/P command byte builder
**Description:** Pure class turning text-only `List<ReceiptLineData>` (`TYPE_TXT`) plus pitch/line-spacing settings into a `byte[]` ESC/P stream: init (`ESC @` = `0x1B 0x40`), pitch/CPI, line spacing, each line's bytes + `CR LF` (`0x0D 0x0A`), and a parameterized (unused until D1) trailing form-feed (`0x0C`). `TYPE_IMG`/`TYPE_BARCODE` lines are skipped with a logged warning, not silently dropped.
**Acceptance criteria:**
- [ ] Unit tests assert exact byte sequences for init, pitch, line-spacing, and CR/LF line termination, citing the ESC/P spec byte values in test comments
- [ ] An `img/png` or `barcode` line is skipped with a logged warning rather than crashing the job
**Verification:** `mvn test` — `EscpCommandBuilderTest` with byte-array equality assertions.
**Dependencies:** A1.
**Files:**
- `src/main/java/id/modefashion/printer/escp/EscpCommandBuilder.java`
- `src/test/java/id/modefashion/printer/escp/EscpCommandBuilderTest.java`
**Estimated scope:** S/M (2 files).

### Task C2: `escp.*` config keys + `EscpConfig` accessor
**Description:** Add a documented `escp.*` section to `printer.properties` (same file, same `PropertiesConfiguration` object): at minimum `escp.printer.name`, `escp.pitch`/`escp.cpi`, `escp.line.spacing`. `EscpConfig` wraps reads with sane defaults so tests can construct an in-memory `PropertiesConfiguration` without touching the real file.
**Acceptance criteria:**
- [ ] `printer.properties` gains a commented `escp.*` section
- [ ] `EscpConfigTest` confirms correct reads and defaults when a key is missing
- [ ] `PrinterGuiApp`'s existing config load/save still works unchanged with the new keys present
**Verification:** Unit test (`EscpConfigTest`); manual — launch `PrinterGuiApp`, open "Edit printer.properties", Save, confirm `escp.*` keys survive the round-trip.
**Dependencies:** A2.
**Files:**
- `printer.properties`
- `src/main/java/id/modefashion/printer/escp/EscpConfig.java`
- `src/test/java/id/modefashion/printer/escp/EscpConfigTest.java`
**Estimated scope:** XS/S (3 files).

### Task C3: Wire `EscpPrinterBackend` to real bytes + transport
**Description:** Replace the B3 stub body: builds bytes via `EscpCommandBuilder` (with `formFeed` still hardcoded `false`), resolves the raw queue via `JavaxRawPrintTransport`/`EscpConfig`, writes the bytes, logs success or a loud error on failure (no retry queue yet).
**Acceptance criteria:**
- [ ] `EscpPrinterBackendTest` (Mockito-mocked `RawPrintTransport`) asserts expected bytes and exactly one `transport.write(bytes)` call
- [ ] Real `{"printer":"escp",...}` WebSocket message produces an actual physical (or captured-byte) printout with correctly formatted text
- [ ] Missing/misconfigured `escp.printer.name` produces a loud logged error, no fallback to another printer
**Verification:** Unit test with mocked transport; manual end-to-end WebSocket → physical/captured printout on both OSes.
**Dependencies:** A2, B3, C1, C2.
**Files:**
- `src/main/java/id/modefashion/printer/backend/EscpPrinterBackend.java`
- `src/test/java/id/modefashion/printer/backend/EscpPrinterBackendTest.java`
**Estimated scope:** S/M (2 files).

### Task D1: Wire the real `formFeed` flag
**Description:** Flip the hardcoded `false` from C3 to the actual `ReceiptData.formFeed` value parsed by `JobRouter`.
**Acceptance criteria:**
- [ ] Unit test: `formFeed=true` job's bytes end with `0x0C`; `formFeed=false` job's bytes contain no `0x0C`
- [ ] Two consecutive real jobs (`formFeed=false` then `formFeed=true`) produce continuous output for the first and a physical form advance only after the second
**Verification:** `EscpCommandBuilderTest` additions; manual two-job WebSocket sequence + physical observation.
**Dependencies:** C1, C3.
**Files:**
- `src/main/java/id/modefashion/printer/escp/EscpCommandBuilder.java`
- `src/main/java/id/modefashion/printer/backend/EscpPrinterBackend.java`
- `src/test/java/id/modefashion/printer/escp/EscpCommandBuilderTest.java`
**Estimated scope:** XS/S (3 files).

### Task E1: In-memory bounded queue + background consumer
**Description:** `EscpJobQueue` — bounded queue (e.g. `ArrayBlockingQueue<EscpJob>`, capacity from `escp.queue.capacity`) + single background consumer calling `EscpPrinterBackend`. The escp path now enqueues instead of printing synchronously. No retry yet — a failure just logs and moves on. Explicitly not disk-backed: a process restart loses queued-but-unprinted jobs (accepted tradeoff).
**Acceptance criteria:**
- [ ] `EscpJobQueueTest` (fake backend) confirms in-order enqueue/drain and correct at-capacity behavior
- [ ] Live WebSocket: escp job returns from `onMessage` immediately (enqueued); printing happens asynchronously, observable via log timestamps
**Verification:** Unit test with fake backend; manual WebSocket + log timestamp inspection.
**Dependencies:** C3/D1.
**Files:**
- `src/main/java/id/modefashion/printer/backend/EscpJobQueue.java`
- `src/main/java/id/modefashion/printer/backend/JobRouter.java` (enqueue instead of direct call)
- `src/test/java/id/modefashion/printer/backend/EscpJobQueueTest.java`
**Estimated scope:** S/M (3 files).

### Task E2: Retry + backoff + cap + loud alert
**Description:** Consumer catches write failures, retries with configurable backoff/cap, tracks "printer down since" state, emits a distinctly-taggable `ALERT` log line when the cap or a down-duration threshold is hit. Overflow policy (reject-new / drop-oldest / hold-indefinitely) is config-driven (`escp.queue.overflow.policy`), not hardcoded — default value needs human sign-off (open question).
**Acceptance criteria:**
- [ ] `EscpJobQueueRetryTest`: scripted-failure fake transport triggers retries per policy, then the cap/alert log line, then recovers once the fake transport succeeds
- [ ] Manual: physically disconnect/misconfigure the LX-300, send jobs, observe retry logs + alert trigger; reconnect, observe backlog drains
**Verification:** Unit test with scripted-failure fake transport; manual offline/reconnect drill.
**Dependencies:** E1.
**Files:**
- `src/main/java/id/modefashion/printer/backend/EscpJobQueue.java` (extended)
- `src/main/java/id/modefashion/printer/backend/RetryPolicy.java`
- `src/test/java/id/modefashion/printer/backend/EscpJobQueueRetryTest.java`
**Estimated scope:** S/M (3 files) — keep separate from E1, don't bundle.

### Task F1: Sample config + deployment notes
**Description:** Finalize `escp.*` property comments; add a short deployment note capturing the OS-specific raw-queue setup validated in A2, so ops can reproduce it on new machines.
**Acceptance criteria:**
- [ ] Deployment note walks through creating the raw queue on both Windows and Linux, referencing exact commands/settings validated in A2
**Verification:** Manual — follow the doc on a clean machine, confirm a working `escp.printer.name` queue results.
**Dependencies:** C2, A2.
**Files:**
- `printer.properties`
- `README.md` or `docs/escp-deployment.md`
**Estimated scope:** XS (1-2 files).

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Raw-queue byte fidelity differs Windows vs Linux (driver may translate line endings/inject headers) | High | A2 isolated early spike validates against captured bytes, not just visual printout |
| `Helper`'s silent default-printer fallback pattern gets reused for escp | High | Explicit new lookup path in A2/C2 that fails loud instead |
| ESC/P vs ESC/P2 command-set mismatch for actual LX-300 firmware | Med | C1 kept minimal/isolated with spec-cited unit tests; physical validation at Checkpoint 2 |
| Hardware dependency makes several tasks impossible to fully verify in CI | Med | Pure logic (DTO, routing, byte-building, queue mechanics) stays unit-tested/hardware-independent; hardware verification is explicit manual checkpoints |
| Legacy caller regression | High | B2 makes zero edits to existing rendering code; Checkpoint 1 explicitly re-verifies both legacy shapes unchanged |
| In-memory queue loses data on restart | Low (accepted) | Documented in F1 as a known tradeoff, not a surprise incident |
| `PrinterGuiApp`'s single config-load path breaking on new `escp.*` keys | Low | C2 includes explicit manual load/edit/save regression check |

## Open Questions

- Exact LX-300 connection method in the field (USB-to-parallel / serial / direct parallel) — needed to finalize A2's queue config.
- Exact physical length of the multi-part carbonless forms (for DIP-switch/driver page-length configuration).
- Whether downstream callers will reliably send the new `printer` field, or whether journal/multi-part jobs eventually warrant a distinct message type.
- Default overflow policy for the bounded retry queue (E2) — config-driven, but needs a human-chosen default before shipping.
- Confirmation that ESC/P (not ESC/P2) is the correct command set for the deployed LX-300 firmware.
- Confirmation the printer's own page-length setting is set to infinite/0 in the field.
