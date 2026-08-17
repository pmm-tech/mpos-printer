# LX-300 Continuous-Form Printing

## Problem Statement

How might we route print jobs to an EPSON LX-300 on continuous-form paper — for multi-part carbonless documents and long-run journals — without disrupting the existing thermal/inkjet receipt path?

## Recommended Direction

Add a second print backend built around raw ESC/P byte streaming, selected per job, alongside the existing Graphics2D backend — rather than trying to make the current `PrinterJob`/`Graphics2D`/`Paper` pipeline serve both printer types.

The current pipeline (`ReceiptWorker` → `PosReceipt` → `ReceiptPaper`) rasterizes each job into an AWT `Paper` whose height is computed from line count — a model that assumes one printer, one page-per-job, cut when done. None of that fits dot-matrix continuous form: OS drivers rasterizing AWT graphics onto a 9-pin head is slow, "page height" isn't a meaningful concept on tractor-feed paper, and Code128 barcodes rendered at 9-pin resolution on carbon paper aren't scannable anyway (and carbon copies wouldn't be scanned regardless).

Concretely: introduce a `PrinterBackend` interface with two implementations — the existing Graphics2D backend (untouched, keeps serving the current printer) and a new `EscpPrinterBackend` that streams text lines and ESC/P control bytes (top-of-form, pitch, line feed) directly to the OS's raw print queue, with no `Paper`/page-height math at all. `printer.properties` gains a second named profile; incoming JSON jobs carry which printer they target.

"Continuous stream" is resolved at the *software* level: the backend never forces a page break between jobs. A form-feed is only emitted when a job explicitly asks for one — never, for journal jobs; once per logical document, for multi-part-form jobs. This keeps the stream model simple while still respecting that a physical multi-part form has a fixed, pre-printed length.

Reliability posture for the journal use case: because a dropped audit-journal line is worse than a receipt that fails loudly, the ESC/P backend should sit behind a durable buffered queue rather than print-on-arrival, so printer-offline/out-of-paper doesn't silently lose data.

## Key Assumptions to Validate

- [ ] The LX-300 is reachable via a raw print queue on both target OSes — Windows RAW-datatype queue and Linux CUPS raw queue (`lp -o raw`) — validate against the actual adapter/connection used in the field (USB-to-parallel vs serial vs direct parallel).
- [ ] ESC/P (not ESC/P2) is the correct command set for the deployed LX-300 units — confirm firmware/model variant.
- [ ] The printer's own page-length setting (DIP switch or driver default) can be set to "infinite/0" so it doesn't impose its own page breaks on top of the app's.
- [ ] Multi-part carbonless forms have a known, fixed physical length (for top-of-form alignment to pre-printed boxes) — confirm with whoever supplies the forms.
- [ ] Downstream callers (cashier/dashboard) can be updated to include a target-printer field on print job payloads without breaking existing integrations.

## MVP Scope

**In:**
- `PrinterBackend` interface; existing Graphics2D path refactored behind it, unchanged in behavior.
- New `EscpPrinterBackend`: plain text lines only, explicit form-feed-on-request, configurable pitch/line-spacing via ESC/P control bytes.
- Second named printer profile in `printer.properties`; job payload gains a target-printer field.
- Raw queue output on both Windows and Linux.
- Buffered/queued writes for the ESC/P backend so a temporarily offline/out-of-paper printer doesn't drop journal data.

**Out (see Not Doing):**
- Barcode or image rendering on the ESC/P backend.
- GUI changes to `PrinterGuiApp` for selecting/monitoring the second printer.
- Automatic top-of-form template alignment for pre-printed form fields.
- Paper-out/jam sensor handling beyond what the OS queue reports.

## Not Doing (and Why)

- **Barcode/image rendering in ESC/P bit-image mode** — low value: 9-pin bit-image is slow to send and carbon-copy paper isn't scanned regardless.
- **Generalized N-printer plugin registry** — no third printer type exists yet; two concrete backends behind one interface is enough until there's real demand for more.
- **CUPS filter / OS-level translation (pushing ESC/P generation out of the app)** — the app already owns rendering logic for the existing printer; splitting that across an externally-maintained filter adds ops burden for no clear win.
- **GUI work in `PrinterGuiApp`** — config/CLI-level for v1; the operator-facing surface can follow once the backend is proven.
- **Template-aware top-of-form alignment** — depends on knowing the exact preprinted form layout, which isn't settled yet; v1 assumes a fixed form length is enough to keep forms in registration, precise field alignment is a v2 refinement.

## Open Questions

- Exact LX-300 connection method in the field (USB-to-parallel adapter, serial, direct parallel) — determines raw-queue setup on each OS.
- Exact physical length of the multi-part carbonless forms, for top-of-form/page-length configuration.
- Should the target-printer field live on the existing JSON receipt schema, or should journal/multi-part jobs be a distinct WebSocket message type from callers?
- What should happen to a queued journal job if the printer stays offline/out-of-paper for an extended period — cap the buffer, alert, or just hold indefinitely?
