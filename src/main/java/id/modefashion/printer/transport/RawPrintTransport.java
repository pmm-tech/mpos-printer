package id.modefashion.printer.transport;

/**
 * Writes raw, untranslated bytes to a print queue. Implementations must not
 * fall back to any printer other than the one explicitly named - a job
 * intended for a raw ESC/P device must never silently land on another
 * printer's queue.
 */
public interface RawPrintTransport {

  void write(byte[] data) throws PrintTransportException;
}
