package id.modefashion.printer.backend;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.modefashion.printer.dto.ReceiptLineData;

/**
 * Decorates an EscpPrintAttempt (the real EscpPrinterBackend in production)
 * with a bounded in-memory queue and a single background consumer thread,
 * so callers (JobRouter) never block on the printer and never need to know
 * queueing exists - this class is itself a PrinterBackend.
 *
 * Deliberately NOT disk-backed: a process restart loses whatever is still
 * queued (accepted tradeoff, see docs/ideas/lx300-continuous-form-printing.md).
 *
 * Retry: each job is retried (same job, in place - the consumer thread
 * blocks/backs off rather than moving on) up to retryPolicy.maxAttempts()
 * times. If it still hasn't succeeded, the job is dropped and a single
 * [ALERT]-tagged log line is emitted - retrying forever would let one
 * broken job (or an offline printer) wedge the whole queue. Once any job
 * succeeds again, the "printer down since" state clears (recovery).
 *
 * Queue-overflow behavior (what happens when the queue itself is full) is
 * unchanged from E1 - reject-new/drop-newest via offer(), logged via
 * droppedCount(). Making that policy itself configurable
 * (escp.queue.overflow.policy, e.g. drop-oldest or hold-indefinitely) was
 * deliberately left out of this task's scope - it's a separate decision
 * (hold-indefinitely in particular risks blocking the WebSocket handler
 * thread) and remains an open question, not silently resolved here.
 */
public class EscpJobQueue implements PrinterBackend {

  private static final Logger logger = LoggerFactory.getLogger(EscpJobQueue.class);

  private final EscpPrintAttempt delegate;
  private final int capacity;
  private final RetryPolicy retryPolicy;
  private final BlockingQueue<EscpJob> queue;
  private final AtomicInteger droppedCount = new AtomicInteger(0);
  private final AtomicInteger retryExhaustedCount = new AtomicInteger(0);
  private final AtomicLong printerDownSinceMillis = new AtomicLong(0);
  private volatile boolean running = false;
  private Thread consumerThread;

  public EscpJobQueue(EscpPrintAttempt delegate, int capacity, RetryPolicy retryPolicy) {
    this.delegate = delegate;
    this.capacity = capacity;
    this.retryPolicy = retryPolicy;
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

  int retryExhaustedCount() {
    return retryExhaustedCount.get();
  }

  private void consumeLoop() {
    while (running) {
      try {
        EscpJob job = queue.take();
        logger.debug("Dequeued ESC/P job ({} line(s), formFeed={})",
            job.data == null ? 0 : job.data.size(), job.formFeed);
        attemptWithRetry(job);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void attemptWithRetry(EscpJob job) {
    int maxAttempts = retryPolicy.maxAttempts();
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      boolean success = delegate.tryPrint(job.data, job.formFeed);
      if (success) {
        markRecoveredIfWasDown();
        return;
      }
      logger.warn("ESC/P print attempt {}/{} failed", attempt, maxAttempts);
      if (attempt < maxAttempts) {
        sleep(retryPolicy.backoffMillis());
      }
    }
    markDownAndAlert(maxAttempts);
  }

  private void markRecoveredIfWasDown() {
    long downSince = printerDownSinceMillis.getAndSet(0);
    if (downSince != 0) {
      long downMillis = System.currentTimeMillis() - downSince;
      logger.warn("[ALERT] ESC/P printer recovered after {} ms down", downMillis);
    }
  }

  private void markDownAndAlert(int maxAttempts) {
    printerDownSinceMillis.compareAndSet(0, System.currentTimeMillis());
    long downMillis = System.currentTimeMillis() - printerDownSinceMillis.get();
    int exhausted = retryExhaustedCount.incrementAndGet();
    logger.error("[ALERT] ESC/P job dropped after {} failed attempts; printer down for {} ms; "
        + "total jobs dropped by retry exhaustion: {}", maxAttempts, downMillis, exhausted);
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
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
