package id.modefashion.printer.escp;

import org.apache.commons.configuration.PropertiesConfiguration;

/**
 * Reads the escp.* keys from the shared printer.properties
 * PropertiesConfiguration, with sane defaults, so tests can supply an
 * in-memory config without touching the real file.
 */
public class EscpConfig {

  private static final int DEFAULT_PITCH_CPI = 10;
  private static final int DEFAULT_LINE_SPACING_UNITS = 30; // 30/180" = 1/6" (standard 6 LPI)
  private static final int DEFAULT_QUEUE_CAPACITY = 100;

  private final PropertiesConfiguration config;

  public EscpConfig(PropertiesConfiguration config) {
    this.config = config;
  }

  public String printerName() {
    return config.getString("escp.printer.name", "");
  }

  public int pitchCpi() {
    return config.getInt("escp.pitch", DEFAULT_PITCH_CPI);
  }

  public int lineSpacingUnits() {
    return config.getInt("escp.line.spacing", DEFAULT_LINE_SPACING_UNITS);
  }

  public int queueCapacity() {
    return config.getInt("escp.queue.capacity", DEFAULT_QUEUE_CAPACITY);
  }
}
