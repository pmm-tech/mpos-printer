package id.modefashion.printer.escp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import id.modefashion.printer.dto.ReceiptLineData;

public class EscpCommandBuilderTest {

  private static List<ReceiptLineData> singleTextLine(String content) {
    return Collections.singletonList(new ReceiptLineData(ReceiptLineData.TYPE_TXT, content));
  }

  @Test
  public void startsWithInitializeCommand() {
    // ESC/P "Initialize Printer": ESC @ = 0x1B 0x40 (EPSON ESC/P Reference Manual)
    byte[] result = new EscpCommandBuilder(10, 30).build(singleTextLine("hi"), false);

    assertArrayEquals(new byte[] { 0x1B, 0x40 }, Arrays.copyOfRange(result, 0, 2));
  }

  @Test
  public void tenCpiUsesEscPCommand() {
    // ESC/P "Select 10 cpi (Pica)": ESC P = 0x1B 0x50
    byte[] result = new EscpCommandBuilder(10, 30).build(singleTextLine("hi"), false);

    assertArrayEquals(new byte[] { 0x1B, 0x50 }, Arrays.copyOfRange(result, 2, 4));
  }

  @Test
  public void twelveCpiUsesEscMCommand() {
    // ESC/P "Select 12 cpi (Elite)": ESC M = 0x1B 0x4D
    byte[] result = new EscpCommandBuilder(12, 30).build(singleTextLine("hi"), false);

    assertArrayEquals(new byte[] { 0x1B, 0x4D }, Arrays.copyOfRange(result, 2, 4));
  }

  @Test
  public void lineSpacingUsesEsc3NCommand() {
    // ESC/P "Set n/180-inch line spacing": ESC 3 n = 0x1B 0x33 n
    byte[] result = new EscpCommandBuilder(10, 30).build(singleTextLine("hi"), false);

    assertArrayEquals(new byte[] { 0x1B, 0x33, 30 }, Arrays.copyOfRange(result, 4, 7));
  }

  @Test
  public void textLineIsTerminatedWithCrLf() {
    byte[] result = new EscpCommandBuilder(10, 30).build(singleTextLine("hi"), false);

    byte[] expectedTail = { 'h', 'i', 0x0D, 0x0A };
    byte[] actualTail = Arrays.copyOfRange(result, result.length - 4, result.length);
    assertArrayEquals(expectedTail, actualTail);
  }

  @Test
  public void unsupportedLineTypesAreSkippedNotFatal() {
    List<ReceiptLineData> lines = Arrays.asList(
        new ReceiptLineData(ReceiptLineData.TYPE_IMG, "base64stuff"),
        new ReceiptLineData(ReceiptLineData.TYPE_BARCODE, "12345"),
        new ReceiptLineData(ReceiptLineData.TYPE_TXT, "only this prints"));

    byte[] result = new EscpCommandBuilder(10, 30).build(lines, false);
    String resultAsText = new String(result);

    assertTrue(resultAsText.contains("only this prints"));
    assertFalse(resultAsText.contains("base64stuff"));
    assertFalse(resultAsText.contains("12345"));
  }

  @Test
  public void formFeedByteAppendedOnlyWhenRequested() {
    byte[] withFeed = new EscpCommandBuilder(10, 30).build(singleTextLine("x"), true);
    byte[] withoutFeed = new EscpCommandBuilder(10, 30).build(singleTextLine("x"), false);

    assertEquals((byte) 0x0C, withFeed[withFeed.length - 1]);
    assertFalse(containsByte(withoutFeed, (byte) 0x0C));
  }

  private static boolean containsByte(byte[] array, byte value) {
    for (byte b : array) {
      if (b == value) {
        return true;
      }
    }
    return false;
  }
}
