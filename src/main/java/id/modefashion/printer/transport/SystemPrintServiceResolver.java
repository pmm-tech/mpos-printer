package id.modefashion.printer.transport;

import java.util.Arrays;
import java.util.Optional;
import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.AttributeSet;

/**
 * Looks up an OS print queue by exact name. Unlike
 * {@link id.modefashion.printer.util.Helper#findPrinterByName}, this never
 * falls back to the system default printer - streaming raw ESC/P control
 * bytes at the wrong device can jam it or produce garbage output.
 */
public class SystemPrintServiceResolver implements PrintServiceResolver {

  @Override
  public Optional<PrintService> findByName(String name) {
    PrintService[] services = PrintServiceLookup.lookupPrintServices((DocFlavor) null, (AttributeSet) null);
    return Arrays.stream(services)
        .filter(service -> service.getName().trim().equals(name))
        .findFirst();
  }
}
