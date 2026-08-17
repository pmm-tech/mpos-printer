package id.modefashion.printer.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import org.junit.Test;

public class ReceiptDataTest {

  private final Gson gson = new Gson();

  @Test
  public void deserializesEscpWrapperShape() {
    String json = "{\"printer\":\"escp\",\"formFeed\":true,"
        + "\"data\":[{\"type\":\"string\",\"content\":\"hello\"}]}";

    ReceiptData receiptData = gson.fromJson(json, ReceiptData.class);

    assertEquals("escp", receiptData.getPrinter());
    assertTrue(receiptData.isFormFeed());
    assertEquals(1, receiptData.getData().size());
    assertEquals("hello", receiptData.getData().get(0).getContent());
  }

  @Test
  public void printerDefaultsToNullAndFormFeedToFalseWhenAbsent() {
    String json = "{\"data\":[{\"type\":\"string\",\"content\":\"hello\"}]}";

    ReceiptData receiptData = gson.fromJson(json, ReceiptData.class);

    assertNull(receiptData.getPrinter());
    assertFalse(receiptData.isFormFeed());
  }
}
