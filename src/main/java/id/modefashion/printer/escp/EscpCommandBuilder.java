package id.modefashion.printer.escp;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.modefashion.printer.dto.ReceiptLineData;

/**
 * Builds a raw ESC/P byte stream from text-only receipt lines - no
 * Paper/page-height math, just initialize + pitch/line-spacing commands,
 * then each line's bytes terminated by CR LF, and (only when asked) a
 * trailing form feed. Command bytes per the EPSON ESC/P Reference Manual.
 */
public class EscpCommandBuilder {

  private static final Logger logger = LoggerFactory.getLogger(EscpCommandBuilder.class);

  private static final byte ESC = 0x1B;
  private static final byte[] INITIALIZE = { ESC, 0x40 };   // ESC @
  private static final byte[] PITCH_10_CPI = { ESC, 0x50 }; // ESC P (Pica)
  private static final byte[] PITCH_12_CPI = { ESC, 0x4D }; // ESC M (Elite)
  private static final byte[] CRLF = { 0x0D, 0x0A };
  private static final byte FORM_FEED = 0x0C;

  private final int pitchCpi;
  private final int lineSpacingUnits; // n/180 inch, used with ESC 3 n

  public EscpCommandBuilder(int pitchCpi, int lineSpacingUnits) {
    this.pitchCpi = pitchCpi;
    this.lineSpacingUnits = lineSpacingUnits;
  }

  public byte[] build(List<ReceiptLineData> lines, boolean formFeed) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writeBytes(out, INITIALIZE);
    writeBytes(out, pitchCommand());
    writeBytes(out, lineSpacingCommand());

    for (ReceiptLineData line : lines) {
      if (ReceiptLineData.TYPE_TXT.equalsIgnoreCase(line.getType())) {
        writeBytes(out, line.getContent().getBytes(StandardCharsets.US_ASCII));
        writeBytes(out, CRLF);
      } else {
        logger.warn("Skipping unsupported ESC/P line type '{}' (ESC/P backend is text-only)", line.getType());
      }
    }

    if (formFeed) {
      out.write(FORM_FEED);
    }

    return out.toByteArray();
  }

  private byte[] pitchCommand() {
    return pitchCpi == 12 ? PITCH_12_CPI : PITCH_10_CPI;
  }

  private byte[] lineSpacingCommand() {
    return new byte[] { ESC, 0x33, (byte) lineSpacingUnits }; // ESC 3 n
  }

  private static void writeBytes(ByteArrayOutputStream out, byte[] bytes) {
    out.write(bytes, 0, bytes.length);
  }
}
