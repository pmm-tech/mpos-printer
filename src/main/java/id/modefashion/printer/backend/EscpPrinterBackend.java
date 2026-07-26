package id.modefashion.printer.backend;

import java.util.List;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.modefashion.printer.dto.ReceiptLineData;
import id.modefashion.printer.escp.EscpCommandBuilder;
import id.modefashion.printer.escp.EscpConfig;
import id.modefashion.printer.transport.JavaxRawPrintTransport;
import id.modefashion.printer.transport.PrintTransportException;
import id.modefashion.printer.transport.RawPrintTransport;

/**
 * Renders text-only ESC/P output and writes it straight to a raw OS print
 * queue - no Graphics2D, no Paper/page-height math. formFeed is not yet
 * wired through (hardcoded false at the builder call site) - see task D1.
 */
public class EscpPrinterBackend implements PrinterBackend {

  private static final Logger logger = LoggerFactory.getLogger(EscpPrinterBackend.class);

  private final EscpConfig escpConfig;
  private final RawPrintTransport transport;
  private final EscpCommandBuilder commandBuilder;

  public EscpPrinterBackend(PropertiesConfiguration config) {
    this(new EscpConfig(config));
  }

  private EscpPrinterBackend(EscpConfig escpConfig) {
    this(escpConfig,
        new JavaxRawPrintTransport(escpConfig.printerName()),
        new EscpCommandBuilder(escpConfig.pitchCpi(), escpConfig.lineSpacingUnits()));
  }

  EscpPrinterBackend(EscpConfig escpConfig, RawPrintTransport transport, EscpCommandBuilder commandBuilder) {
    this.escpConfig = escpConfig;
    this.transport = transport;
    this.commandBuilder = commandBuilder;
  }

  @Override
  public void print(List<ReceiptLineData> data, boolean formFeed) {
    byte[] bytes = commandBuilder.build(data, false); // formFeed wired in D1
    try {
      transport.write(bytes);
      logger.info("ESC/P job printed: {} line(s) -> queue '{}'",
          data == null ? 0 : data.size(), escpConfig.printerName());
    } catch (PrintTransportException e) {
      logger.error("ESC/P print failed for queue '{}': {}", escpConfig.printerName(), e.getMessage(), e);
    }
  }
}
