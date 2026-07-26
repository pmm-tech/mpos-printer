package id.modefashion.printer.backend;

import java.util.List;

import org.apache.commons.configuration.PropertiesConfiguration;

import id.modefashion.printer.dto.ReceiptLineData;
import id.modefashion.printer.worker.ReceiptWorker;
import id.modefashion.printer.worker.ReceiptWorkerString;

/**
 * Wraps the existing Graphics2D/javax.print rendering path unchanged. formFeed
 * is accepted for interface symmetry with EscpPrinterBackend but has no
 * meaning here - this path always prints one Paper-sized page per job.
 */
public class Graphics2DPrinterBackend implements PrinterBackend {

  private final PropertiesConfiguration config;

  public Graphics2DPrinterBackend(PropertiesConfiguration config) {
    this.config = config;
  }

  @Override
  public void print(List<ReceiptLineData> data, boolean formFeed) {
    new ReceiptWorker(data, config).proceed();
  }

  public void printLegacyString(String data) {
    new ReceiptWorkerString(data, config).proceed();
  }
}
