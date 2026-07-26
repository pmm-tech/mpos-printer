package id.modefashion.printer.backend;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import id.modefashion.printer.dto.ReceiptLineData;
import id.modefashion.printer.escp.EscpCommandBuilder;
import id.modefashion.printer.escp.EscpConfig;
import id.modefashion.printer.transport.PrintTransportException;
import id.modefashion.printer.transport.RawPrintTransport;

public class EscpPrinterBackendTest {

  private static EscpConfig testConfig(String printerName) {
    PropertiesConfiguration props = new PropertiesConfiguration();
    props.setProperty("escp.printer.name", printerName);
    props.setProperty("escp.pitch", "10");
    props.setProperty("escp.line.spacing", "30");
    return new EscpConfig(props);
  }

  @Test
  public void writesExpectedBytesExactlyOnceToTransport_formFeedNotWiredYet() throws Exception {
    EscpConfig escpConfig = testConfig("LX300_TEST");
    RawPrintTransport transport = mock(RawPrintTransport.class);
    EscpCommandBuilder commandBuilder = new EscpCommandBuilder(escpConfig.pitchCpi(), escpConfig.lineSpacingUnits());
    EscpPrinterBackend backend = new EscpPrinterBackend(escpConfig, transport, commandBuilder);
    List<ReceiptLineData> data = Collections.singletonList(new ReceiptLineData(ReceiptLineData.TYPE_TXT, "hello"));

    // formFeed=true passed in, but C3 hardcodes false at the builder call site (D1 wires it up)
    backend.print(data, true);

    byte[] expectedBytes = commandBuilder.build(data, false);
    ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(transport, times(1)).write(bytesCaptor.capture());
    assertArrayEquals(expectedBytes, bytesCaptor.getValue());
  }

  @Test
  public void logsLoudlyAndDoesNotThrowWhenTransportFails() throws Exception {
    EscpConfig escpConfig = testConfig("missing-queue");
    RawPrintTransport transport = mock(RawPrintTransport.class);
    doThrow(new PrintTransportException("queue not found")).when(transport).write(any());
    EscpCommandBuilder commandBuilder = new EscpCommandBuilder(10, 30);
    EscpPrinterBackend backend = new EscpPrinterBackend(escpConfig, transport, commandBuilder);
    List<ReceiptLineData> data = Collections.singletonList(new ReceiptLineData(ReceiptLineData.TYPE_TXT, "x"));

    backend.print(data, false);

    verify(transport, times(1)).write(any());
  }
}
