package id.modefashion.printer.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import id.modefashion.printer.dto.ReceiptLineData;

public class JobRouterTest {

  @Test
  public void wrapperWithEscpPrinterRoutesToEscpBackend() {
    Graphics2DPrinterBackend graphics2D = mock(Graphics2DPrinterBackend.class);
    PrinterBackend escp = mock(PrinterBackend.class);
    JobRouter router = new JobRouter(graphics2D, escp);
    String message = "{\"printer\":\"escp\",\"formFeed\":true,"
        + "\"data\":[{\"type\":\"string\",\"content\":\"hi\"}]}";

    router.route(message);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ReceiptLineData>> dataCaptor = ArgumentCaptor.forClass(List.class);
    verify(escp).print(dataCaptor.capture(), eq(true));
    assertEquals(1, dataCaptor.getValue().size());
    assertEquals("hi", dataCaptor.getValue().get(0).getContent());
    verifyNoInteractions(graphics2D);
  }

  @Test
  public void wrapperWithEscpPrinterIsCaseInsensitive() {
    Graphics2DPrinterBackend graphics2D = mock(Graphics2DPrinterBackend.class);
    PrinterBackend escp = mock(PrinterBackend.class);
    JobRouter router = new JobRouter(graphics2D, escp);
    String message = "{\"printer\":\"ESCP\",\"data\":[{\"type\":\"string\",\"content\":\"hi\"}]}";

    router.route(message);

    verify(escp).print(org.mockito.ArgumentMatchers.any(), eq(false));
    verifyNoInteractions(graphics2D);
  }

  @Test
  public void wrapperWithoutPrinterFieldRoutesToGraphics2D() {
    Graphics2DPrinterBackend graphics2D = mock(Graphics2DPrinterBackend.class);
    PrinterBackend escp = mock(PrinterBackend.class);
    JobRouter router = new JobRouter(graphics2D, escp);
    String message = "{\"data\":[{\"type\":\"string\",\"content\":\"hi\"}]}";

    router.route(message);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ReceiptLineData>> dataCaptor = ArgumentCaptor.forClass(List.class);
    verify(graphics2D).print(dataCaptor.capture(), eq(false));
    assertEquals(1, dataCaptor.getValue().size());
    verifyNoInteractions(escp);
  }

  @Test
  public void wrapperWithOtherPrinterValueRoutesToGraphics2D() {
    Graphics2DPrinterBackend graphics2D = mock(Graphics2DPrinterBackend.class);
    PrinterBackend escp = mock(PrinterBackend.class);
    JobRouter router = new JobRouter(graphics2D, escp);
    String message = "{\"printer\":\"thermal\",\"data\":[{\"type\":\"string\",\"content\":\"hi\"}]}";

    router.route(message);

    verify(graphics2D).print(org.mockito.ArgumentMatchers.any(), eq(false));
    verifyNoInteractions(escp);
  }

  @Test
  public void legacyBareArrayRoutesToGraphics2D() {
    Graphics2DPrinterBackend graphics2D = mock(Graphics2DPrinterBackend.class);
    PrinterBackend escp = mock(PrinterBackend.class);
    JobRouter router = new JobRouter(graphics2D, escp);
    String message = "[{\"type\":\"string\",\"content\":\"legacy line\"}]";

    router.route(message);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ReceiptLineData>> dataCaptor = ArgumentCaptor.forClass(List.class);
    verify(graphics2D).print(dataCaptor.capture(), eq(false));
    assertEquals(1, dataCaptor.getValue().size());
    assertEquals("legacy line", dataCaptor.getValue().get(0).getContent());
    verifyNoInteractions(escp);
  }

  @Test
  public void legacyHashDelimitedStringRoutesToGraphics2DLegacyStringMethod() {
    Graphics2DPrinterBackend graphics2D = mock(Graphics2DPrinterBackend.class);
    PrinterBackend escp = mock(PrinterBackend.class);
    JobRouter router = new JobRouter(graphics2D, escp);
    String message = "line one#line two#line three";

    router.route(message);

    verify(graphics2D).printLegacyString(message);
    verifyNoInteractions(escp);
  }

  @Test
  public void legacyDetectionStillHingesOnTypeSubstring_regressionOfExistingBehavior() {
    // Documents the pre-existing (unchanged) fragile discriminator: any
    // non-'{' message containing the substring "type" is treated as the
    // bare-array JSON shape, exactly as PrintServer.onMessage did before.
    Graphics2DPrinterBackend graphics2D = mock(Graphics2DPrinterBackend.class);
    PrinterBackend escp = mock(PrinterBackend.class);
    JobRouter router = new JobRouter(graphics2D, escp);
    String message = "[{\"type\":\"string\",\"content\":\"x\"}]";

    router.route(message);

    assertTrue(message.contains("type"));
    verify(graphics2D).print(org.mockito.ArgumentMatchers.any(), eq(false));
  }
}
