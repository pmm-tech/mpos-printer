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

## Confirmed connection method: USB-to-serial

Per the official EPSON LX-300+ Printer's Guide
(`https://files.support.epson.com/pdf/lx300p/lx300ppg.pdf`), the printer has **no native
USB port** — only "1 standard bidirectional, 8-bit parallel interface with IEEE-1284
nibble mode support, and 1 EIA-232D serial interface." The field connection is a
USB-to-RS232 (serial) adapter into the printer's native serial port.

### Required printer-side default settings (front panel)

These are set via the printer's default-setting mode (hold Tear Off while powering on —
see the manual for the full procedure). All of these must match what the OS/queue side
expects:

| Setting | Required value | Why |
|---|---|---|
| I/F mode | `Serial` (or `Auto`) | Must match the physical connection — this printer defaults to `Auto`, but pin it explicitly to `Serial` to avoid ambiguity. |
| Software | `ESC/P` (not `IBM 2380 Plus`) | Confirmed via spec (`Emulation: EPSON ESC/P® and IBM® 2380 Plus`) — matches `EscpCommandBuilder`'s assumption, but it's a selectable option, not automatic. |
| Baud rate | Match OS-side serial config | Printer supports 19200 / 9600 / 4800 / 2400 / 1200 / 600 / 300 bps. |
| Parity | Match OS-side serial config | Printer supports None / Odd / Even / Ignore. |
| Page length for tractor | Closest match to actual form length, **max 17 in** | See "Page length caveat" below — there is no infinite/0 option on this hardware. |

## Linux / macOS (CUPS)

CUPS is the same stack on both — a raw queue configured on macOS should behave
identically on Linux.

1. Connect the LX-300 via the USB-to-serial adapter.
2. Create a raw queue pointed at the serial device, using CUPS' built-in `raw` model
   (no PPD, no filter chain):
   ```sh
   # Serial device path varies by OS/adapter - check with `ls /dev/tty.*` (macOS) or
   # `ls /dev/ttyUSB*` (Linux), or `lpinfo -v` for what CUPS detects:
   sudo lpadmin -p LX300_RAW -E -v serial:/dev/ttyUSB0?baud=9600 -m raw
   ```
3. Confirm the queue exists and is enabled: `lpstat -p LX300_RAW`.
4. Set `escp.printer.name = LX300_RAW` in `printer.properties` (must match exactly).
5. Validate byte fidelity before trusting it: run
   `java -cp target/classes id.modefashion.printer.transport.RawPrintTransportManualHarness LX300_RAW`
   and confirm the physical printout matches exactly what was sent (no dropped/altered
   bytes, correct line endings).

## Windows

1. Connect the LX-300 via the USB-to-serial adapter (Windows will enumerate it as a
   `COMx` port).
2. In "Devices and Printers" (or Print Management), add a printer using the
   **"Generic / Text Only"** driver — this avoids any driver-side translation.
3. Bind it to the `COMx` port the adapter enumerated as. In the port's properties, set
   the baud rate/parity to match the printer's front-panel serial settings above.
4. Name the queue something exact and unambiguous (e.g. `LX300_RAW`) and use that exact
   name for `escp.printer.name`.
5. Validate the same way as Linux/macOS — run `RawPrintTransportManualHarness` against
   the queue name and physically confirm the printout.

## Page length caveat (no "infinite" setting on this hardware)

The original design assumption was that the printer's page-length setting could be set
to "infinite/0" so it wouldn't impose its own page breaks. **That option doesn't exist
on the LX-300.** The front-panel "Page length for tractor" setting is a fixed list:
`3, 3.5, 4, 5.5, 6, 7, 8, 8.5, 11, 70/6 (≈11.67), 12, 14, 17` inches — **max 17 in**,
while the printer's continuous/multipart paper handling spec goes up to 22 in. If actual
forms exceed 17 in, pick the closest available value; per the manual this mainly affects
the printer's own auto-tear-off/skip-perforation convenience and what a bare hardware
form-feed advances to — it does **not** block our own ESC/P-generated line-feed/form-feed
bytes, and software can override the panel's top-of-form position.

## Open items before this is production-ready

- Byte-fidelity confirmation on an actual Windows box and an actual Linux box (this repo
  only ever confirmed the *code path* works against mocked transports — see `tasks/plan.md`
  for the full list of what's unit-tested vs. what still needs physical/OS validation).
- Exact physical form length in use, to pick the closest "Page length for tractor" value
  (see caveat above).
- Whether the `printer` field should eventually move to a distinct message type as
  ESC/P-specific needs grow — deferred for now, see `tasks/plan.md`.
