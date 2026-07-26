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

- [x] Task B1: Extend `ReceiptData` DTO with `printer`/`formFeed`
- [x] Task B2: `PrinterBackend` interface + `Graphics2DPrinterBackend` (zero behavior change)
- [x] Task B3: `PrintServer` routing → stub `EscpPrinterBackend`

### Checkpoint 1: Routing
- [x] All three message shapes (bare array, `#`-string, new escp wrapper) route correctly (unit-verified via `JobRouterTest`)
- [~] Legacy shapes produce byte-for-byte identical printed output to pre-change behavior — logic unchanged/unit-verified; live physical-printer smoke test intentionally skipped (see B3 notes)
- [x] escp wrapper only logs so far — no printer touched yet

### Phase C: Real ESC/P text output

- [x] Task C1: ESC/P command byte builder (pure, unit-tested)
- [x] Task C2: `escp.*` config keys + `EscpConfig` accessor
- [~] Task C3: Wire `EscpPrinterBackend` to real bytes + transport — unit-verified; real-printer leg blocked, same as A2

### Checkpoint 2: Core value delivery
- [~] A real ESC/P job sent over the WebSocket prints correctly (text only) on the LX-300, on both OSes, synchronously — **code path unit-verified end-to-end (routing → command bytes → transport call); actual physical/OS printer validation is the same open gate as A2**

### Phase D: Form-feed control

- [~] Task D1: Wire the real `formFeed` flag end-to-end — unit-verified; physical validation blocked, same as A2

### Checkpoint 3: Continuous-form model validated
- [~] Multi-part-form jobs (form-feed per document) and journal jobs (never) both physically validated — **logic unit-verified (byte-level + wiring); physical LX-300 observation still open, same gate as A2**

### Phase E: Durability / retry queue

- [x] Task E1: In-memory bounded queue + background consumer (happy path)
- [~] Task E2: Retry + backoff + cap + loud alert logging — unit-verified; physical offline/reconnect drill blocked, same as A2

### Checkpoint 4: Reliability
- [~] Printer-offline scenario fails loudly, not silently, without becoming a disk-persistence project — **retry/alert/recovery logic unit-verified (see E2); physical offline/reconnect drill on a real LX-300 remains open, same blocker as A2**

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

### Task B1: Extend `ReceiptData` DTO — DONE
**Description:** Add `private String printer;` and `private boolean formFeed;` to `dto/ReceiptData.java` (Lombok `@Data` generates accessors).
**Acceptance criteria:**
- [x] `ReceiptData` has `printer`/`formFeed` fields with Lombok-generated getters/setters
- [x] Unit test asserts correct Gson deserialization of `{"printer":"escp","formFeed":true,"data":[...]}`, including `printer == null` when absent
**Verification:** `mvn test` — new `ReceiptDataTest`.
**Dependencies:** A1.
**Files:**
- `src/main/java/id/modefashion/printer/dto/ReceiptData.java`
- `src/test/java/id/modefashion/printer/dto/ReceiptDataTest.java`
**Estimated scope:** XS (2 files).

### Task B2: `PrinterBackend` interface + `Graphics2DPrinterBackend` — DONE
**Description:** Introduce a minimal `PrinterBackend` interface. `Graphics2DPrinterBackend` wraps today's two entry points unchanged: delegates to `new ReceiptWorker(data, config).proceed()` and `new ReceiptWorkerString(dataString, config).proceed()`, with zero changes to `ReceiptWorker`, `ReceiptWorkerString`, `PosReceipt`, `PosReceiptString`, or `ReceiptPaper`.
**Acceptance criteria:**
- [x] `Graphics2DPrinterBackend` compiles and calls through to existing workers with identical arguments/order
- [x] No changes to any file under `worker/` or `paper/` (verified via `git status`)
**Verification:** Manual smoke test — send a pre-existing bare-array message and a pre-existing `#`-delimited message through the new wrapper and confirm identical printed output to before the change.
**Dependencies:** None.
**Files:**
- `src/main/java/id/modefashion/printer/backend/PrinterBackend.java`
- `src/main/java/id/modefashion/printer/backend/Graphics2DPrinterBackend.java`
**Estimated scope:** S (2 files).

**Implementation notes:**
- `PrinterBackend` has one method: `print(List<ReceiptLineData> data, boolean formFeed)`. `formFeed` is part of the shared interface for symmetry with the upcoming `EscpPrinterBackend` (which cares about it); `Graphics2DPrinterBackend` just ignores it, which is correct (no continuous-form concept on that path).
- The legacy `#`-delimited string case is **not** on the `PrinterBackend` interface at all — it only ever goes to Graphics2D (no ESC/P equivalent exists or is planned), so `Graphics2DPrinterBackend` exposes it as a separate concrete method `printLegacyString(String)`, called directly by `JobRouter` (B3) rather than through polymorphic dispatch.

### Task B3: `PrintServer` routing → stub `EscpPrinterBackend` — DONE (unit-verified; live-WebSocket leg deliberately skipped, see notes)
**Description:** Extract dispatch out of `PrintServer.onMessage` into a testable `JobRouter`. Routing order: (1) message starts with `{` and contains `"printer"` → parse as `ReceiptData` wrapper; `printer == "escp"` (case-insensitive) → `EscpPrinterBackend` (stub: logs job line count + `formFeed`, does not print); wrapper without/other `printer` → `Graphics2DPrinterBackend` with wrapper's data. (2) Else existing `message.contains("type")` check, byte-for-byte preserved → bare array → Graphics2D. (3) Else `#`-delimited string → Graphics2D. `PrintServer.onMessage` becomes a thin call into `JobRouter.route(message, config)`.
**Acceptance criteria:**
- [x] `JobRouterTest` (unit, no live WebSocket) asserts all four routing branches (plus case-insensitivity and the "other printer value" case — 7 tests total)
- [ ] Live WebSocket: pre-existing bare-array and `#`-string messages produce identical printed output to pre-change behavior — **not run, see notes**
- [ ] Live WebSocket: new escp wrapper message produces a stub log line, no print attempt/error — **not run, see notes**
**Verification:** Unit test (`JobRouterTest`); manual WebSocket client message + log inspection for live end-to-end + regression check.
**Dependencies:** B1, B2.
**Files:**
- `src/main/java/id/modefashion/printer/PrintServer.java`
- `src/main/java/id/modefashion/printer/backend/JobRouter.java`
- `src/main/java/id/modefashion/printer/backend/EscpPrinterBackend.java` (stub)
- `src/test/java/id/modefashion/printer/backend/JobRouterTest.java`
**Estimated scope:** S/M (4 files).

**Implementation notes:**
- Wrapper-shape detection ended up simpler than the plan's literal wording: `trimmed.startsWith("{")` alone is the discriminator, **not** also requiring the substring `"printer"`. Reasoning: the legacy bare-array format is always a top-level `[`, and the legacy `#`-string format is never JSON, so no existing caller can produce a top-level `{` message today — checking for `{` alone unambiguously identifies the new wrapper shape, including the "wrapper without a printer field" case the plan itself calls out (which a `contains("\"printer\"")` check would have missed entirely, since that field would be absent by definition).
- Branch 2's discriminator (`message.contains("type")`) is untouched byte-for-byte from the original `PrintServer.onMessage`, including its known fragility (any non-`{` message that happens to contain the substring "type" is treated as JSON) — that's pre-existing behavior, not introduced or fixed here.
- **Live-WebSocket regression/smoke verification was deliberately not run.** This machine's `printer.properties` points at a real, currently-configured `EPSON_L3250_Series` CUPS printer — actually starting `PrintServer` and sending a legacy message would risk triggering a real physical print job as a side effect of verification, which felt like the wrong tradeoff for a routing-logic check that unit tests already cover thoroughly (7 tests across all branches, including asserting the escp branch never touches the Graphics2D mock and vice versa). If you want this leg closed, run the app and send a bare-array and a `#`-string message manually.

### Task C1: ESC/P command byte builder — DONE
**Description:** Pure class turning text-only `List<ReceiptLineData>` (`TYPE_TXT`) plus pitch/line-spacing settings into a `byte[]` ESC/P stream: init (`ESC @` = `0x1B 0x40`), pitch/CPI, line spacing, each line's bytes + `CR LF` (`0x0D 0x0A`), and a parameterized (unused until D1) trailing form-feed (`0x0C`). `TYPE_IMG`/`TYPE_BARCODE` lines are skipped with a logged warning, not silently dropped.
**Acceptance criteria:**
- [x] Unit tests assert exact byte sequences for init, pitch, line-spacing, and CR/LF line termination, citing the ESC/P spec byte values in test comments
- [x] An `img/png` or `barcode` line is skipped with a logged warning rather than crashing the job
**Verification:** `mvn test` — `EscpCommandBuilderTest` with byte-array equality assertions.
**Dependencies:** A1.
**Files:**
- `src/main/java/id/modefashion/printer/escp/EscpCommandBuilder.java`
- `src/test/java/id/modefashion/printer/escp/EscpCommandBuilderTest.java`
**Estimated scope:** S/M (2 files).

**Implementation notes:**
- Line spacing uses `ESC 3 n` (0x1B 0x33 n, "set n/180-inch line spacing") rather than the discrete `ESC 0`/`ESC 2` presets, so the config value maps directly to a single byte parameter instead of needing a lookup table.
- Constructor takes primitives (`pitchCpi`, `lineSpacingUnits`), not an `EscpConfig` object — keeps C1 independent of C2 per the dependency graph; C3 wires the two together.
- Also included a `formFeed` byte-presence/absence test at the builder level here (not just in D1) since it's basic correctness of the builder itself, independent of whether any caller wires the real flag through yet.

### Task C2: `escp.*` config keys + `EscpConfig` accessor — DONE
**Description:** Add a documented `escp.*` section to `printer.properties` (same file, same `PropertiesConfiguration` object): at minimum `escp.printer.name`, `escp.pitch`/`escp.cpi`, `escp.line.spacing`. `EscpConfig` wraps reads with sane defaults so tests can construct an in-memory `PropertiesConfiguration` without touching the real file.
**Acceptance criteria:**
- [x] `printer.properties` gains a commented `escp.*` section
- [x] `EscpConfigTest` confirms correct reads and defaults when a key is missing
- [x] `PrinterGuiApp`'s existing config load/save still works unchanged with the new keys present
**Verification:** Unit test (`EscpConfigTest`); manual — launch `PrinterGuiApp`, open "Edit printer.properties", Save, confirm `escp.*` keys survive the round-trip.
**Dependencies:** A2.
**Files:**
- `printer.properties`
- `src/main/java/id/modefashion/printer/escp/EscpConfig.java`
- `src/test/java/id/modefashion/printer/escp/EscpConfigTest.java`
**Estimated scope:** XS/S (3 files).

**Implementation notes:**
- `escp.line.spacing` default is `30` (30/180" = 1/6", the standard 6 LPI default) — matches conventional receipt/journal line spacing.
- `escp.printer.name` defaults to empty string, not a placeholder value — `EscpPrinterBackend` (C3) must treat blank the same as "not configured" and fail loud rather than attempt a lookup for `""`.
- Verified the GUI round-trip **without launching the actual Swing app** (no interactive display session in this environment): wrote a throwaway harness that loads a scratch copy of `printer.properties` into `PropertiesConfiguration`, calls `setProperty` only on the keys `PrinterGuiApp.showConfigDialog()` actually touches (`printer.port`, `printer.name`), then `.save()` — exactly what the GUI's Save button does — then reloads and confirms `escp.*` keys and values survived. They did. The real `printer.properties` was never touched by this check (operated on a copy in the scratch dir).

### Task C3: Wire `EscpPrinterBackend` to real bytes + transport — DONE (unit-verified; real-printer leg blocked, same as A2)
**Description:** Replace the B3 stub body: builds bytes via `EscpCommandBuilder` (with `formFeed` still hardcoded `false`), resolves the raw queue via `JavaxRawPrintTransport`/`EscpConfig`, writes the bytes, logs success or a loud error on failure (no retry queue yet).
**Acceptance criteria:**
- [x] `EscpPrinterBackendTest` (Mockito-mocked `RawPrintTransport`) asserts expected bytes and exactly one `transport.write(bytes)` call
- [ ] Real `{"printer":"escp",...}` WebSocket message produces an actual physical (or captured-byte) printout with correctly formatted text — **NOT DONE, same blocker as A2 (no Windows/Linux/physical LX-300 access; sudo has no TTY here for a local CUPS test queue)**
- [x] Missing/misconfigured `escp.printer.name` produces a loud logged error, no fallback to another printer
**Verification:** Unit test with mocked transport; manual end-to-end WebSocket → physical/captured printout on both OSes.
**Dependencies:** A2, B3, C1, C2.
**Files:**
- `src/main/java/id/modefashion/printer/backend/EscpPrinterBackend.java`
- `src/test/java/id/modefashion/printer/backend/EscpPrinterBackendTest.java`
**Estimated scope:** S/M (2 files).

**Implementation notes:**
- Constructor pattern matches `Graphics2DPrinterBackend`/`JavaxRawPrintTransport`: a public `EscpPrinterBackend(PropertiesConfiguration)` for production wiring, plus a package-private `EscpPrinterBackend(EscpConfig, RawPrintTransport, EscpCommandBuilder)` seam for tests. Tests use a *real* `EscpConfig`/`EscpCommandBuilder` (both already independently unit-tested, deterministic) and only mock the actual I/O boundary (`RawPrintTransport`) — less mocking, more representative test.
- `escp.printer.name` defaults to `""` (C2), and `SystemPrintServiceResolver` never matches an empty name, so an unconfigured ESC/P backend fails loud by default out of the box — verified via `EscpPrinterBackendTest.logsLoudlyAndDoesNotThrowWhenTransportFails`, which also confirms `print()` never throws out of the `PrinterBackend` interface (matches the existing codebase's pattern of catching and logging print failures rather than propagating them into the WebSocket handler).
- `print()` still calls `commandBuilder.build(data, false)` regardless of the `formFeed` argument it receives — explicitly tested (`writesExpectedBytesExactlyOnceToTransport_formFeedNotWiredYet`) so D1's change has a clear before/after.
- `PrintServer` now constructs `new EscpPrinterBackend(config)` instead of B3's no-arg stub.
- Real-printer end-to-end verification is the same open gate as A2 — no new attempt was made here since the blocker (no Windows/Linux/physical hardware access, no sudo TTY) is identical and already tracked there.

### Task D1: Wire the real `formFeed` flag — DONE (unit-verified; physical form-advance observation blocked, same as A2)
**Description:** Flip the hardcoded `false` from C3 to the actual `ReceiptData.formFeed` value parsed by `JobRouter`.
**Acceptance criteria:**
- [x] Unit test: `formFeed=true` job's bytes end with `0x0C`; `formFeed=false` job's bytes contain no `0x0C` (already covered by C1's `EscpCommandBuilderTest.formFeedByteAppendedOnlyWhenRequested`, at the builder level)
- [ ] Two consecutive real jobs (`formFeed=false` then `formFeed=true`) produce continuous output for the first and a physical form advance only after the second — **NOT DONE, same blocker as A2 (no physical LX-300)**
**Verification:** `EscpCommandBuilderTest` additions; manual two-job WebSocket sequence + physical observation.
**Dependencies:** C1, C3.
**Files:**
- `src/main/java/id/modefashion/printer/backend/EscpPrinterBackend.java`
- `src/test/java/id/modefashion/printer/backend/EscpPrinterBackendTest.java`
**Estimated scope:** XS/S (3 files).

**Implementation notes:**
- One-line change: `commandBuilder.build(data, false)` → `commandBuilder.build(data, formFeed)`. Byte-level form-feed correctness was already covered by C1's `EscpCommandBuilderTest`; this task's own test (`EscpPrinterBackendTest.formFeedFlagIsPassedThroughToCommandBuilder`) proves the *wiring* — that the flag `JobRouter` parsed off `ReceiptData` actually reaches the builder call, not just that the builder itself handles the flag correctly in isolation. Also renamed/simplified the C3-era `..._formFeedNotWiredYet` test now that it is wired.
- Physical form-advance timing observation (two real jobs, one plain one form-feeding) needs a real LX-300 and is out of reach here — tracked as the same open gate as A2/C3.

### Task E1: In-memory bounded queue + background consumer — DONE
**Description:** `EscpJobQueue` — bounded queue (e.g. `ArrayBlockingQueue<EscpJob>`, capacity from `escp.queue.capacity`) + single background consumer calling `EscpPrinterBackend`. The escp path now enqueues instead of printing synchronously. No retry yet — a failure just logs and moves on. Explicitly not disk-backed: a process restart loses queued-but-unprinted jobs (accepted tradeoff).
**Acceptance criteria:**
- [x] `EscpJobQueueTest` (fake backend) confirms in-order enqueue/drain and correct at-capacity behavior
- [x] Live WebSocket: escp job returns from `onMessage` immediately (enqueued); printing happens asynchronously — verified at the unit level (`drainsJobsInOrderToDelegateBackend` starts the real consumer thread and polls for async delivery); not re-verified over an actual live WebSocket connection, which would need the same live-server smoke test already skipped in B3/C3 for the same reason (avoiding a real print job on this machine's configured printer)
**Verification:** Unit test with fake backend; manual WebSocket + log timestamp inspection.
**Dependencies:** C3/D1.
**Files:**
- `src/main/java/id/modefashion/printer/backend/EscpJobQueue.java`
- `src/main/java/id/modefashion/printer/PrintServer.java` (wiring)
- `src/main/java/id/modefashion/printer/escp/EscpConfig.java` (new `queueCapacity()`)
- `printer.properties` (new `escp.queue.capacity` key)
- `src/test/java/id/modefashion/printer/backend/EscpJobQueueTest.java`
- `src/test/java/id/modefashion/printer/escp/EscpConfigTest.java` (extended)
**Estimated scope:** S/M (3 files).

**Implementation notes:**
- **Design deviation from the plan's file list**: `JobRouter` was NOT touched. Instead, `EscpJobQueue implements PrinterBackend` and *decorates* the real `EscpPrinterBackend` — the consumer thread dequeues and calls the wrapped backend. `PrintServer` wires `new EscpJobQueue(new EscpPrinterBackend(config), capacity)` as the escp backend it hands to `JobRouter`, which is completely unaware queueing exists. Smaller diff, one clean seam, and `JobRouterTest`'s existing routing assertions stay valid unchanged.
- Added `escp.queue.capacity` (default 100) to `EscpConfig`/`printer.properties` — not originally listed under C2 since the need wasn't known until this task, but it's an `escp.*` key so it belongs in the same accessor for consistency.
- Overflow behavior: `queue.offer()` (non-blocking) — a full queue drops the new job (not the caller's, not a random one) and logs a loud `ERROR` with a running dropped-job count, tested by starting the queue *without* calling `start()` so the consumer never drains it, making the capacity boundary deterministic to assert.
- Consumer thread is a daemon thread; `stop()` interrupts it. `PrintServer` never calls `stop()` today (no shutdown hook exists elsewhere in the app either) — acceptable for now since a daemon thread doesn't block JVM exit, but worth revisiting if a graceful-shutdown story is ever needed.
- `EscpPrinterBackend` itself was not modified — it still catches and logs `PrintTransportException` internally rather than throwing, so E1's "failure just logs and moves on" requirement is satisfied for free by the existing C3 behavior. E2 will need to reconsider this if retry requires visibility into success/failure.

### Task E2: Retry + backoff + cap + loud alert
**Description:** Consumer catches write failures, retries with configurable backoff/cap, tracks "printer down since" state, emits a distinctly-taggable `ALERT` log line when the cap or a down-duration threshold is hit. Overflow policy (reject-new / drop-oldest / hold-indefinitely) is config-driven (`escp.queue.overflow.policy`), not hardcoded — default value needs human sign-off (open question).
**Acceptance criteria:**
- [x] `EscpJobQueueRetryTest`: scripted-failure fake transport triggers retries per policy, then the cap/alert log line, then recovers once the fake transport succeeds
- [ ] Manual: physically disconnect/misconfigure the LX-300, send jobs, observe retry logs + alert trigger; reconnect, observe backlog drains — **NOT DONE, same blocker as A2 (no physical LX-300)**
**Verification:** Unit test with scripted-failure fake transport; manual offline/reconnect drill.
**Dependencies:** E1.
**Files:**
- `src/main/java/id/modefashion/printer/backend/EscpJobQueue.java` (extended)
- `src/main/java/id/modefashion/printer/backend/RetryPolicy.java`
- `src/main/java/id/modefashion/printer/backend/EscpPrintAttempt.java` (new — see notes)
- `src/main/java/id/modefashion/printer/backend/EscpPrinterBackend.java` (implements the new interface)
- `src/main/java/id/modefashion/printer/escp/EscpConfig.java` (new `retryMaxAttempts()`/`retryBackoffMillis()`)
- `printer.properties` (new `escp.retry.*` keys)
- `src/test/java/id/modefashion/printer/backend/EscpJobQueueRetryTest.java`
- `src/test/java/id/modefashion/printer/backend/EscpJobQueueTest.java` (updated for new constructor/delegate type)
**Estimated scope:** S/M (3 files) — keep separate from E1, don't bundle.

**Implementation notes:**
- **New seam required and not anticipated by the plan**: `EscpPrinterBackend.print()` (the `PrinterBackend` interface method) swallows `PrintTransportException` and returns `void` — the consumer had no way to know if a print actually succeeded, which retry logic needs. Added `EscpPrintAttempt` (`boolean tryPrint(data, formFeed)`); `EscpPrinterBackend` now implements both interfaces, with `print()` just forwarding to `tryPrint()` and discarding the result (existing `EscpPrinterBackendTest` assertions unaffected). `EscpJobQueue`'s delegate type changed from `PrinterBackend` to `EscpPrintAttempt` accordingly — a legitimate, expected change to an "extended" E1 file, not scope creep.
- **Retry keeps retrying the same job in place** (the single consumer thread blocks/backs off), rather than moving to the next job and coming back — a receipt/journal line shouldn't silently lose its place in line. After `retryMaxAttempts` (default 3, fixed `retryBackoffMillis` interval, default 1000ms — not exponential, kept simple for MVP) all fail, the job is dropped and a single `[ALERT]`-tagged `ERROR` log line fires (not one per attempt) with the down-duration and a running `retryExhaustedCount()`. The *next* job to succeed logs `[ALERT] ... recovered after N ms down` and clears the down-since state. Verified directly in test log output: 3 failed attempts → `[ALERT] ... dropped` → next job succeeds → `[ALERT] ... recovered`.
- **Deliberately out of scope**: `escp.queue.overflow.policy` (what happens when the *queue itself* is full, as opposed to the printer failing) is explicitly mentioned in this task's description but its acceptance criteria don't actually test it — E1's behavior (reject-new via `queue.offer()`, logged, counted) is unchanged. Making this config-driven (especially a "hold-indefinitely" option, which risks blocking the WebSocket handler thread) is left as the open question the plan already carries forward, not silently resolved here.
- Physical offline/reconnect drill against a real LX-300 remains the same blocked gate as A2/C3/D1.

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
