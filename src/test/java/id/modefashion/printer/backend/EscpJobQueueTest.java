package id.modefashion.printer.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import id.modefashion.printer.dto.ReceiptLineData;

public class EscpJobQueueTest {

  private EscpJobQueue queue;

  @After
  public void tearDown() {
    if (queue != null) {
      queue.stop();
    }
  }

  private static List<ReceiptLineData> lineOf(String content) {
    return Collections.singletonList(new ReceiptLineData(ReceiptLineData.TYPE_TXT, content));
  }

  @Test
  public void drainsJobsInOrderToDelegateBackend() throws InterruptedException {
    RecordingBackend delegate = new RecordingBackend();
    queue = new EscpJobQueue(delegate, 10);
    queue.start();

    queue.print(lineOf("a"), false);
    queue.print(lineOf("b"), false);
    queue.print(lineOf("c"), true);

    awaitUntil(() -> delegate.received.size() == 3);

    assertEquals("a", delegate.received.get(0).data.get(0).getContent());
    assertEquals("b", delegate.received.get(1).data.get(0).getContent());
    assertEquals("c", delegate.received.get(2).data.get(0).getContent());
    assertEquals(false, delegate.received.get(0).formFeed);
    assertEquals(true, delegate.received.get(2).formFeed);
  }

  @Test
  public void dropsAndCountsJobsWhenFullAndNotDraining() {
    RecordingBackend delegate = new RecordingBackend();
    queue = new EscpJobQueue(delegate, 2); // capacity 2, consumer deliberately not started

    queue.print(lineOf("1"), false);
    queue.print(lineOf("2"), false);
    queue.print(lineOf("3"), false); // should be dropped, queue is full

    assertEquals(2, queue.size());
    assertEquals(1, queue.droppedCount());
    assertTrue(delegate.received.isEmpty());
  }

  private static void awaitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 2000;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(10);
    }
    throw new AssertionError("condition not met within timeout");
  }

  private static final class RecordedCall {
    final List<ReceiptLineData> data;
    final boolean formFeed;

    RecordedCall(List<ReceiptLineData> data, boolean formFeed) {
      this.data = data;
      this.formFeed = formFeed;
    }
  }

  private static final class RecordingBackend implements PrinterBackend {
    final List<RecordedCall> received = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void print(List<ReceiptLineData> data, boolean formFeed) {
      received.add(new RecordedCall(data, formFeed));
    }
  }
}
