package id.modefashion.printer.escp;

import static org.junit.Assert.assertEquals;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.junit.Test;

public class EscpConfigTest {

  @Test
  public void readsConfiguredValues() {
    PropertiesConfiguration props = new PropertiesConfiguration();
    props.setProperty("escp.printer.name", "LX300_RAW");
    props.setProperty("escp.pitch", "12");
    props.setProperty("escp.line.spacing", "24");
    props.setProperty("escp.queue.capacity", "50");
    props.setProperty("escp.retry.max.attempts", "5");
    props.setProperty("escp.retry.backoff.ms", "2000");

    EscpConfig config = new EscpConfig(props);

    assertEquals("LX300_RAW", config.printerName());
    assertEquals(12, config.pitchCpi());
    assertEquals(24, config.lineSpacingUnits());
    assertEquals(50, config.queueCapacity());
    assertEquals(5, config.retryMaxAttempts());
    assertEquals(2000, config.retryBackoffMillis());
  }

  @Test
  public void fallsBackToDefaultsWhenKeysMissing() {
    PropertiesConfiguration props = new PropertiesConfiguration();

    EscpConfig config = new EscpConfig(props);

    assertEquals("", config.printerName());
    assertEquals(10, config.pitchCpi());
    assertEquals(30, config.lineSpacingUnits());
    assertEquals(100, config.queueCapacity());
    assertEquals(3, config.retryMaxAttempts());
    assertEquals(1000, config.retryBackoffMillis());
  }
}
