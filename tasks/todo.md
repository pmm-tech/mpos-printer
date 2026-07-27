# To-Do: ESC/P Raw Printing for EPSON LX-300 Continuous Form

See `tasks/plan.md` for full task details, acceptance criteria, and verification steps.

## Phase A: Foundation & risk reduction
- [x] A1: Test scaffolding (JUnit4/5 + Mockito) — XS
- [~] A2: Raw-queue transport spike — code+unit tests done; **hardware validation blocked (no Windows/Linux/physical LX-300 access from this sandbox — open gate before Phase C ships)**

**Checkpoint:** `mvn test` runs with new stack (done); transport byte-fidelity on real OSes/hardware — **still open**

## Phase B: Message routing (backward compatible)
- [x] B1: Extend `ReceiptData` DTO with `printer`/`formFeed` — XS
- [x] B2: `PrinterBackend` interface + `Graphics2DPrinterBackend` (zero behavior change) — S
- [x] B3: `PrintServer` routing → stub `EscpPrinterBackend` — S/M (unit-verified; live-printer smoke test intentionally skipped to avoid triggering a real print job)

**Checkpoint 1:** all 3 message shapes route correctly (done); legacy shapes byte-for-byte unchanged (done); escp wrapper only logs so far (done)

## Phase C: Real ESC/P text output
- [x] C1: ESC/P command byte builder (pure, unit-tested) — S/M
- [x] C2: `escp.*` config keys + `EscpConfig` accessor — XS/S
- [x] C3: Wire `EscpPrinterBackend` to real bytes + transport — S/M (unit-verified; real-printer leg blocked, same as A2)

**Checkpoint 2 (core value delivery):** code path unit-verified end-to-end (done); real ESC/P job printing correctly on physical LX-300, both OSes — **still open, same blocker as A2**

## Phase D: Form-feed control
- [x] D1: Wire real `formFeed` flag end-to-end — XS/S (unit-verified; physical validation blocked, same as A2)

**Checkpoint 3:** multi-part-form (feed per doc) and journal (never) jobs both logic-verified (done); physical LX-300 validation — **still open, same blocker as A2**

## Phase E: Durability / retry queue
- [x] E1: In-memory bounded queue + background consumer — S/M (JobRouter untouched; EscpJobQueue decorates EscpPrinterBackend instead)
- [x] E2: Retry + backoff + cap + loud alert logging — S/M (unit-verified; physical drill blocked, same as A2)

**Checkpoint 4:** printer-offline scenario fails loudly, not silently (unit-verified); no disk persistence added (done by design); physical offline/reconnect drill — **still open, same blocker as A2**

## Phase F: Polish (optional, non-gating)
- [x] F1: Sample config + deployment notes — XS (written as an explicitly-flagged unvalidated guide, since A2 never completed real-queue validation)

**Final checkpoint:** all unit-testable acceptance criteria met (30/30 tests passing, full build green); real-OS/hardware validation (A2/C3/D1/E2) remains one open gate — ready for review, not yet ready to ship to a real LX-300 without that validation

## Phase G: GUI configuration for ESC/P (added on the same branch/PR)
- [x] G1: Add `escp.*` fields to `PrinterGuiApp`'s config dialog — S

**Checkpoint G:** dialog shows and saves all 6 `EscpConfig` keys, matching existing dialog behavior/conventions; visually verified via a real launch on this machine's console session — confirmed by user

---

## Open questions — resolution status

- [x] **Connection method**: USB-to-serial. Confirmed correct against the official LX-300+ spec — the printer has no native USB, only "1 standard bidirectional, 8-bit parallel interface with IEEE-1284 nibble mode support, and 1 EIA-232D serial interface." A USB-to-RS232 adapter into the native serial port is the right approach. The printer's front-panel **I/F mode** setting must be set to `Serial` (or `Auto`) to match.
- [~] **Physical form length**: official spec says continuous/multipart paper is **4–22 in** length (1 original + up to 4 copies), but the exact length actually used isn't decided yet (depends on the real forms procured) — see the page-length caveat below, which limits what's practically usable.
- [ ] **Schema vs. distinct message type**: still open — user chose to keep the current wrapper approach for now (recommended: don't split speculatively; revisit only if concrete ESC/P-only metadata needs emerge, e.g. form templates/copy count).
- [x] **Queue overflow policy default**: confirmed **reject-new** — matches what's already implemented (E1/E2), no code change needed.
- [x] **ESC/P vs ESC/P2**: confirmed via official spec — `Emulation: EPSON ESC/P® and IBM® 2380 Plus`. Matches `EscpCommandBuilder`'s assumption. Note: the printer also has a front-panel **Software** setting (`ESC/P` / `IBM 2380 Plus`) that must be explicitly set to `ESC/P` — it isn't automatic.
- [~] **"Infinite/0" page length**: **corrected, not confirmed as assumed.** No infinite/0 option exists on this hardware. The front-panel "Page length for tractor" setting is a fixed list: `3, 3.5, 4, 5.5, 6, 7, 8, 8.5, 11, 70/6 (≈11.67), 12, 14, 17` inches — **max 17 in**. If actual forms exceed 17 in (within the printer's 4–22 in handling range), the panel setting can't match exactly; this mainly affects the printer's own auto-tear-off/skip-perforation convenience, not our own ESC/P line/form-feed bytes, and the manual notes software can override the panel's top-of-form position anyway.
