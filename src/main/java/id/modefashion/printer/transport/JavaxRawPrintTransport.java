package id.modefashion.printer.transport;

import java.util.Optional;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends a byte[] verbatim to a named OS print queue via
 * javax.print/DocFlavor.BYTE_ARRAY.AUTOSENSE, bypassing any AWT/Graphics2D
 * rasterization. The queue itself must be configured OS-side as a raw
 * passthrough (a CUPS raw queue on Linux/macOS; a Generic/Text-Only driver on
 * a RAW-datatype port on Windows) for the bytes to reach the device
 * untranslated.
 */
public class JavaxRawPrintTransport implements RawPrintTransport {

  private static final Logger logger = LoggerFactory.getLogger(JavaxRawPrintTransport.class);

  private final String queueName;
  private final PrintServiceResolver resolver;

  public JavaxRawPrintTransport(String queueName) {
    this(queueName, new SystemPrintServiceResolver());
  }

  JavaxRawPrintTransport(String queueName, PrintServiceResolver resolver) {
    this.queueName = queueName;
    this.resolver = resolver;
  }

  @Override
  public void write(byte[] data) throws PrintTransportException {
    Optional<PrintService> service = resolver.findByName(queueName);
    if (!service.isPresent()) {
      throw new PrintTransportException(
          "Raw print queue not found: '" + queueName + "'. Not falling back to another printer.");
    }

    Doc doc = new SimpleDoc(data, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
    DocPrintJob job = service.get().createPrintJob();
    PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
    try {
      job.print(doc, attrs);
      logger.info("Wrote {} raw bytes to queue '{}'", data.length, queueName);
    } catch (PrintException e) {
      throw new PrintTransportException("Failed writing to raw print queue '" + queueName + "'", e);
    }
  }
}
