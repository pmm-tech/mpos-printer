package id.modefashion.printer.dto;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReceiptLineDataTest {

  @Test
  public void constructorSetsTypeAndContent() {
    ReceiptLineData line = new ReceiptLineData(ReceiptLineData.TYPE_TXT, "hello");

    assertEquals(ReceiptLineData.TYPE_TXT, line.getType());
    assertEquals("hello", line.getContent());
  }

  @Test
  public void settersMutateFields() {
    ReceiptLineData line = new ReceiptLineData(ReceiptLineData.TYPE_TXT, "hello");

    line.setType(ReceiptLineData.TYPE_IMG);
    line.setContent("updated");

    assertEquals(ReceiptLineData.TYPE_IMG, line.getType());
    assertEquals("updated", line.getContent());
  }
}
