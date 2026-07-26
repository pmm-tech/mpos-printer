package id.modefashion.printer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.modefashion.printer.backend.EscpJobQueue;
import id.modefashion.printer.backend.EscpPrinterBackend;
import id.modefashion.printer.backend.Graphics2DPrinterBackend;
import id.modefashion.printer.backend.JobRouter;
import id.modefashion.printer.escp.EscpConfig;

public class PrintServer extends WebSocketServer {
  private Set<WebSocket> connections;
  private PropertiesConfiguration config;
  private JobRouter jobRouter;
  private EscpJobQueue escpJobQueue;
  private static final Logger logger = LoggerFactory.getLogger(PrintServer.class);

  public PrintServer(PropertiesConfiguration config) {
    super(new InetSocketAddress(config.getInt("printer.port")));
    this.connections = Collections.synchronizedSet(new HashSet<WebSocket>());
    this.config = config;
    this.escpJobQueue = new EscpJobQueue(new EscpPrinterBackend(config), new EscpConfig(config).queueCapacity());
    this.escpJobQueue.start();
    this.jobRouter = new JobRouter(new Graphics2DPrinterBackend(config), this.escpJobQueue);
  }

  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    logger.info("Connection open");
  }

  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    logger.info("Connection close");
  }

  @Override
  public void onMessage(WebSocket conn, String message) {
    logger.debug("================== NEW RECEIPT MESSAGE ==================");
    jobRouter.route(message);
    logger.debug("================== END RECEIPT MESSAGE ==================");
    // sendResponse("Success");
  }

  @Override
  public void onError(WebSocket conn, Exception ex) {
    logger.error(ex.getMessage(), ex);
  }

  @Override
  public void onStart() {
    logger.info("================== PRINTER IS READY FOR PRINTING ===================");
  }
}
