package id.modefashion.printer.transport;

import java.nio.charset.StandardCharsets;

/**
 * Standalone manual tool for validating raw byte-for-byte delivery to a named
 * print queue - NOT part of the automated test suite (byte fidelity through
 * an OS print queue can only be checked against real queue/hardware output).
 *
 * Usage: java -cp target/classes id.modefashion.printer.transport.RawPrintTransportManualHarness
 * &lt;queueName&gt; [payloadText]
 */
public final class RawPrintTransportManualHarness {

  private RawPrintTransportManualHarness() {
  }

  public static void main(String[] args) throws PrintTransportException {
    if (args.length < 1) {
      System.err.println("Usage: RawPrintTransportManualHarness <queueName> [payloadText]");
      System.exit(1);
      return;
    }

    String queueName = args[0];
    String payloadText = args.length > 1
        ? args[1]
        : "RAW TRANSPORT TEST LINE 1\r\nRAW TRANSPORT TEST LINE 2\r\n";

    byte[] payload = buildPayload(payloadText);

    RawPrintTransport transport = new JavaxRawPrintTransport(queueName);
    transport.write(payload);
    System.out.println("Wrote " + payload.length + " bytes to queue '" + queueName + "'");
  }

  static byte[] buildPayload(String text) {
    byte[] textBytes = text.getBytes(StandardCharsets.US_ASCII);
    byte formFeed = 0x0C;
    byte[] result = new byte[textBytes.length + 1];
    System.arraycopy(textBytes, 0, result, 0, textBytes.length);
    result[textBytes.length] = formFeed;
    return result;
  }
}
