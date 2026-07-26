package id.modefashion.printer.backend;

import java.lang.reflect.Type;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import id.modefashion.printer.dto.ReceiptData;
import id.modefashion.printer.dto.ReceiptLineData;

/**
 * Decides which PrinterBackend a raw WebSocket message goes to.
 *
 * Routing order:
 * 1. Message is a JSON object ({ ... }) -> the new backward-compatible
 *    wrapper shape (ReceiptData: printer/formFeed/data). printer == "escp"
 *    (case-insensitive) goes to the ESC/P backend; anything else (missing
 *    or any other value) goes to Graphics2D, using the wrapper's data.
 * 2. Else, message contains "type" -> legacy bare JSON array of
 *    ReceiptLineData, unchanged from the original PrintServer behavior.
 * 3. Else -> legacy '#'-delimited plain text, unchanged.
 */
public class JobRouter {

  private static final Logger logger = LoggerFactory.getLogger(JobRouter.class);
  private static final Gson GSON = new Gson();
  private static final Type LINE_LIST_TYPE = new TypeToken<List<ReceiptLineData>>() {
  }.getType();
  private static final String ESCP_PRINTER = "escp";

  private final Graphics2DPrinterBackend graphics2DBackend;
  private final PrinterBackend escpBackend;

  public JobRouter(Graphics2DPrinterBackend graphics2DBackend, PrinterBackend escpBackend) {
    this.graphics2DBackend = graphics2DBackend;
    this.escpBackend = escpBackend;
  }

  public void route(String message) {
    String trimmed = message.trim();
    if (trimmed.startsWith("{")) {
      routeWrapper(message);
    } else if (message.contains("type")) {
      List<ReceiptLineData> data = GSON.fromJson(message, LINE_LIST_TYPE);
      graphics2DBackend.print(data, false);
    } else {
      graphics2DBackend.printLegacyString(message);
    }
  }

  private void routeWrapper(String message) {
    ReceiptData wrapper = GSON.fromJson(message, ReceiptData.class);
    if (ESCP_PRINTER.equalsIgnoreCase(wrapper.getPrinter())) {
      logger.debug("Routing wrapper job to ESC/P backend");
      escpBackend.print(wrapper.getData(), wrapper.isFormFeed());
    } else {
      logger.debug("Routing wrapper job to Graphics2D backend (printer={})", wrapper.getPrinter());
      graphics2DBackend.print(wrapper.getData(), wrapper.isFormFeed());
    }
  }
}
