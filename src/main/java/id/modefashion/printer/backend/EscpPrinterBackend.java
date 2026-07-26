package id.modefashion.printer.backend;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.modefashion.printer.dto.ReceiptLineData;

/**
 * STUB (task B3): logs the job it would print and does nothing else. Wired
 * up to real ESC/P rendering + a raw transport in task C3.
 */
public class EscpPrinterBackend implements PrinterBackend {

  private static final Logger logger = LoggerFactory.getLogger(EscpPrinterBackend.class);

  @Override
  public void print(List<ReceiptLineData> data, boolean formFeed) {
    int lineCount = data == null ? 0 : data.size();
    logger.info("[ESC/P STUB] would print {} line(s), formFeed={}", lineCount, formFeed);
  }
}
