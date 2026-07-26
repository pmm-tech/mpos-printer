package id.modefashion.printer.transport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import javax.print.Doc;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class JavaxRawPrintTransportTest {

  @Test
  public void throwsWhenQueueNotFound_noFallback() {
    PrintServiceResolver resolver = mock(PrintServiceResolver.class);
    when(resolver.findByName("missing-queue")).thenReturn(Optional.empty());
    JavaxRawPrintTransport transport = new JavaxRawPrintTransport("missing-queue", resolver);

    PrintTransportException ex = assertThrows(PrintTransportException.class,
        () -> transport.write("hello".getBytes()));

    assertTrue(ex.getMessage().contains("missing-queue"));
    verify(resolver).findByName("missing-queue");
  }

  @Test
  public void writesExactBytesToResolvedQueue() throws Exception {
    PrintServiceResolver resolver = mock(PrintServiceResolver.class);
    PrintService service = mock(PrintService.class);
    DocPrintJob job = mock(DocPrintJob.class);
    when(resolver.findByName("escp-queue")).thenReturn(Optional.of(service));
    when(service.createPrintJob()).thenReturn(job);
    JavaxRawPrintTransport transport = new JavaxRawPrintTransport("escp-queue", resolver);
    byte[] payload = "ESC/P PAYLOAD\r\n\f".getBytes("US-ASCII");

    transport.write(payload);

    ArgumentCaptor<Doc> docCaptor = ArgumentCaptor.forClass(Doc.class);
    verify(job).print(docCaptor.capture(), any());
    assertArrayEquals(payload, (byte[]) docCaptor.getValue().getPrintData());
  }

  @Test
  public void wrapsPrintExceptionAsTransportException() throws Exception {
    PrintServiceResolver resolver = mock(PrintServiceResolver.class);
    PrintService service = mock(PrintService.class);
    DocPrintJob job = mock(DocPrintJob.class);
    when(resolver.findByName("escp-queue")).thenReturn(Optional.of(service));
    when(service.createPrintJob()).thenReturn(job);
    doThrow(new PrintException("printer offline")).when(job).print(any(), any());
    JavaxRawPrintTransport transport = new JavaxRawPrintTransport("escp-queue", resolver);

    PrintTransportException ex = assertThrows(PrintTransportException.class,
        () -> transport.write("x".getBytes()));

    assertEquals(PrintException.class, ex.getCause().getClass());
  }
}
