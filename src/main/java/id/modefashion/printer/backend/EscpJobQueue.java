package id.modefashion.printer.backend;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.modefashion.printer.dto.ReceiptLineData;

/**
 * Decorates a PrinterBackend (the real EscpPrinterBackend in production)
 * with a bounded in-memory queue and a single background consumer thread,
 * so callers (JobRouter) never block on the printer and never need to know
 * queueing exists - this class is itself a PrinterBackend.
 *
 * Deliberately NOT disk-backed: a process restart loses whatever is still
 * queued (accepted tradeoff, see docs/ideas/lx300-continuous-form-printing.md).
 * No retry on delegate failure yet - task E2.
 */
public class EscpJobQueue implements PrinterBackend {

  private static final Logger logger = LoggerFactory.getLogger(EscpJobQueue.class);

  private final PrinterBackend delegate;
  private final int capacity;
  private final BlockingQueue<EscpJob> queue;
  private final AtomicInteger droppedCount = new AtomicInteger(0);
  private volatile boolean running = false;
  private Thread consumerThread;

  public EscpJobQueue(PrinterBackend delegate, int capacity) {
    this.delegate = delegate;
    this.capacity = capacity;
    this.queue = new ArrayBlockingQueue<>(capacity);
  }

  public synchronized void start() {
    if (running) {
      return;
    }
    running = true;
    consumerThread = new Thread(this::consumeLoop, "escp-job-queue-consumer");
    consumerThread.setDaemon(true);
    consumerThread.start();
  }

  public synchronized void stop() {
    running = false;
    if (consumerThread != null) {
      consumerThread.interrupt();
    }
  }

  @Override
  public void print(List<ReceiptLineData> data, boolean formFeed) {
    EscpJob job = new EscpJob(data, formFeed);
    if (!queue.offer(job)) {
      int dropped = droppedCount.incrementAndGet();
      logger.error("ESC/P job queue is full (capacity {}) - dropping job with {} line(s). "
          + "Total dropped since start: {}", capacity, data == null ? 0 : data.size(), dropped);
      return;
    }
    logger.debug("Enqueued ESC/P job ({} line(s), formFeed={}); queue size now {}",
        data == null ? 0 : data.size(), formFeed, queue.size());
  }

  int size() {
    return queue.size();
  }

  int droppedCount() {
    return droppedCount.get();
  }

  private void consumeLoop() {
    while (running) {
      try {
        EscpJob job = queue.take();
        logger.debug("Dequeued ESC/P job ({} line(s), formFeed={})",
            job.data == null ? 0 : job.data.size(), job.formFeed);
        delegate.print(job.data, job.formFeed);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static final class EscpJob {
    private final List<ReceiptLineData> data;
    private final boolean formFeed;

    private EscpJob(List<ReceiptLineData> data, boolean formFeed) {
      this.data = data;
      this.formFeed = formFeed;
    }
  }
}
