# ESC/P Raw Queue Deployment (EPSON LX-300 / continuous form)

> **Status: UNVALIDATED.** These steps were never confirmed against real hardware or a
> real Linux/Windows box — see `tasks/plan.md` (task A2 and its "Implementation notes")
> for why. This document describes the standard, well-documented mechanism for each OS
> (CUPS raw queues on Linux/macOS; a RAW-datatype port on Windows), not something specific
> to this codebase, but **nobody has run these exact steps against an LX-300 yet**. Treat
> this as a starting point, not a checklist to trust blindly. Before relying on it in
> production, complete the open gate: run `RawPrintTransportManualHarness` against the
> queue you create and confirm byte-for-byte fidelity (no line-ending translation, no
> injected headers) with the physical printer.

## What "raw queue" means here

The ESC/P backend (`JavaxRawPrintTransport`) sends a `byte[]` verbatim via
`javax.print`/`DocFlavor.BYTE_ARRAY.AUTOSENSE` to a print queue looked up **by exact
name**. For the bytes to reach the LX-300 untranslated, that queue must be configured
OS-side to skip any filter/driver pipeline that would rewrite them (line-ending
conversion, PostScript conversion, header injection, etc.) — i.e. a genuine raw
passthrough queue, not a normal driver-backed one.

## Linux / macOS (CUPS)

CUPS is the same stack on both — a raw queue configured on macOS should behave
identically on Linux.

1. Connect the LX-300 (parallel, USB-to-parallel adapter, serial, or network/JetDirect —
   confirm the actual connection method in the field; this affects the device URI below).
2. Create a raw queue pointed at the physical device, using CUPS' built-in `raw` model
   (no PPD, no filter chain):
   ```sh
   # USB-to-parallel/serial (device path varies - check `lpinfo -v` for the exact URI):
   sudo lpadmin -p LX300_RAW -E -v usb://EPSON/LX-300 -m raw

   # Network-attached (e.g. USB-to-Ethernet print server, JetDirect/socket protocol):
   sudo lpadmin -p LX300_RAW -E -v socket://<printer-ip>:9100 -m raw
   ```
3. Confirm the queue exists and is enabled: `lpstat -p LX300_RAW`.
4. Set `escp.printer.name = LX300_RAW` in `printer.properties` (must match exactly).
5. Validate byte fidelity before trusting it: run
   `java -cp target/classes id.modefashion.printer.transport.RawPrintTransportManualHarness LX300_RAW`
   and confirm the physical printout matches exactly what was sent (no dropped/altered
   bytes, correct line endings).

## Windows

1. Connect the LX-300 (USB-to-parallel adapter, serial, or network).
2. In "Devices and Printers" (or Print Management), add a printer using the
   **"Generic / Text Only"** driver — this avoids any driver-side translation.
3. Bind it to the correct port:
   - USB-to-parallel/serial: the corresponding `LPTx`/`COMx` port.
   - Network-attached: a **Standard TCP/IP Port**, port 9100 (raw/JetDirect), *not* an
     LPR port (LPR can add its own framing).
4. In the port's configuration, ensure the **protocol is RAW**, not LPR.
5. Name the queue something exact and unambiguous (e.g. `LX300_RAW`) and use that exact
   name for `escp.printer.name`.
6. Validate the same way as Linux/macOS — run `RawPrintTransportManualHarness` against
   the queue name and physically confirm the printout.

## Open items before this is production-ready

Carried over from `tasks/plan.md` (task A2) — these still need a human with access to
the real environment:

- Exact LX-300 connection method in the field (affects the device URI/port above).
- Confirmation that ESC/P (not ESC/P2) is the correct command set for the deployed
  firmware/model variant.
- The printer's own page-length/DIP-switch setting should be infinite/0 so it doesn't
  fight the software-only continuous-form model.
- Byte-fidelity confirmation on an actual Windows box and an actual Linux box (this repo
  only ever confirmed the *code path* works against mocked transports — see `tasks/plan.md`
  for the full list of what's unit-tested vs. what still needs physical/OS validation).
