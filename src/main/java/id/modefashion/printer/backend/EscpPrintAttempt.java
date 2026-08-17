package id.modefashion.printer.backend;

import java.util.List;

import id.modefashion.printer.dto.ReceiptLineData;

/**
 * Narrower than PrinterBackend: reports success/failure instead of
 * swallowing it, so a retrying caller (EscpJobQueue) can tell whether to
 * try again. EscpPrinterBackend implements both this and PrinterBackend -
 * print() just forwards to tryPrint() and discards the result, preserving
 * the existing no-throw contract for direct callers.
 */
public interface EscpPrintAttempt {

  boolean tryPrint(List<ReceiptLineData> data, boolean formFeed);
}
