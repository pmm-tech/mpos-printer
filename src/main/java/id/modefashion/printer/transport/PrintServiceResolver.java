package id.modefashion.printer.transport;

import java.util.Optional;
import javax.print.PrintService;

public interface PrintServiceResolver {

  Optional<PrintService> findByName(String name);
}
