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
- [ ] C2: `escp.*` config keys + `EscpConfig` accessor — XS/S
- [ ] C3: Wire `EscpPrinterBackend` to real bytes + transport — S/M

**Checkpoint 2 (core value delivery):** real ESC/P job over WebSocket prints correctly on LX-300, both OSes, synchronously

## Phase D: Form-feed control
- [ ] D1: Wire real `formFeed` flag end-to-end — XS/S

**Checkpoint 3:** multi-part-form (feed per doc) and journal (never) jobs both physically validated

## Phase E: Durability / retry queue
- [ ] E1: In-memory bounded queue + background consumer — S/M
- [ ] E2: Retry + backoff + cap + loud alert logging — S/M

**Checkpoint 4:** printer-offline scenario fails loudly, not silently; no disk persistence added

## Phase F: Polish (optional, non-gating)
- [ ] F1: Sample config + deployment notes — XS

**Final checkpoint:** all acceptance criteria met across A–E; ready for review

---

## Open questions to resolve before/alongside implementation
- [ ] Exact LX-300 connection method in the field (USB-to-parallel / serial / direct parallel)
- [ ] Exact physical length of the multi-part carbonless forms
- [ ] Should `printer` field live on existing schema long-term, or a distinct message type eventually?
- [ ] Default overflow policy for the E2 retry queue (reject-new / drop-oldest / hold-indefinitely)
- [ ] Confirm ESC/P (not ESC/P2) is correct for the deployed LX-300 firmware
- [ ] Confirm printer's own page-length setting is infinite/0 in the field
