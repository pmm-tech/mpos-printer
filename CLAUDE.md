# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Plain Java 8 app (Maven, no Spring Boot). `App.java` launches a Swing GUI (`PrinterGuiApp`) which starts `PrintServer`, a `Java-WebSocket` server that receives print jobs as JSON and renders them to a physical receipt printer and/or PDF via PDFBox. Barcodes are rendered with ZXing.

## Build & Run

```sh
./mvnw clean package     # build; output: target/printer-1.0-SNAPSHOT-jar-with-dependencies.jar
./mvnw exec:java          # run for development (main class: id.modefashion.printer.App)
java -jar target/printer-1.0-SNAPSHOT-jar-with-dependencies.jar   # run the packaged fat jar
```

There is no `spring-boot:run` target — this project does not use Spring Boot, despite what the top-level `mpos/CLAUDE.md` workspace summary says.

### Build JDK gotcha

Lombok (`@Data`/`@AllArgsConstructor` on the `dto/` classes) silently fails to generate any methods if built with a JDK Lombok doesn't yet support (observed: JDK 26 — compilation fails downstream with "cannot find symbol: method getX()" and no mention of Lombok in the error). This is a Maven-in-process-compiler + Lombok-internals issue, not fixable with `--add-opens`. Build with JDK 21 (present locally). A `.java-version` file pins this for `jenv`, but if your shell exports `JAVA_HOME` directly (check `echo $JAVA_HOME`), that env var wins over `jenv` — export it explicitly instead: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

## Configuration

Printer name, WebSocket port (default 20001), paper size/margins, and font are all set in `printer.properties` at the repo root — edit this file directly rather than hardcoding values in source.

## Structure

- `App.java` — entry point, wires up logging then launches the GUI
- `PrinterGuiApp.java` — Swing GUI for operating/monitoring the printer
- `PrintServer.java` — WebSocket server accepting print job JSON
- `worker/` — receipt rendering/printing logic (`ReceiptWorker`, `PosReceipt`, barcode handling)
- `paper/ReceiptPaper.java` — paper/layout model
- `dto/` — JSON payload shapes (`ReceiptData`, `ReceiptLineData`)
