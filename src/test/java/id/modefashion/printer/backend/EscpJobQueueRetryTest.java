package id.modefashion.printer.backend;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

import id.modefashion.printer.dto.ReceiptLineData;

public class EscpJobQueueRetryTest {

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

  @Test
  public void succeedsWithinRetryBudgetWithoutBeingCountedAsExhausted() throws InterruptedException {
    ScriptedTransport fake = new ScriptedTransport(1); // fails once, then always succeeds
    RetryPolicy policy = new RetryPolicy(3, 10);
    queue = new EscpJobQueue(fake, 10, policy);
    queue.start();

    queue.print(lineOf("a"), false);

    awaitUntil(() -> fake.succeeded.size() == 1);
    assertEquals("a", fake.succeeded.get(0).get(0).getContent());
    assertEquals(0, queue.retryExhaustedCount());
  }

  @Test
  public void exhaustsRetriesThenDropsJob_andRecoversForNextJob() throws InterruptedException {
    // Fails exactly the first 3 calls (== maxAttempts), then always succeeds.
    ScriptedTransport fake = new ScriptedTransport(3);
    RetryPolicy policy = new RetryPolicy(3, 10);
    queue = new EscpJobQueue(fake, 10, policy);
    queue.start();

    queue.print(lineOf("a"), false); // consumes calls 1,2,3 - all fail -> exhausted, dropped

    awaitUntil(() -> queue.retryExhaustedCount() == 1);
    assertEquals(0, fake.succeeded.size());

    queue.print(lineOf("b"), false); // call 4 - fake now succeeds

    awaitUntil(() -> fake.succeeded.size() == 1);
    assertEquals("b", fake.succeeded.get(0).get(0).getContent());
    assertEquals(1, queue.retryExhaustedCount());
  }

  private static final class ScriptedTransport implements EscpPrintAttempt {
    private final AtomicInteger callCount = new AtomicInteger(0);
    private final int failuresBeforeSuccess;
    final List<List<ReceiptLineData>> succeeded = Collections.synchronizedList(new ArrayList<>());

    ScriptedTransport(int failuresBeforeSuccess) {
      this.failuresBeforeSuccess = failuresBeforeSuccess;
    }

    @Override
    public boolean tryPrint(List<ReceiptLineData> data, boolean formFeed) {
      int call = callCount.incrementAndGet();
      if (call <= failuresBeforeSuccess) {
        return false;
      }
      succeeded.add(data);
      return true;
    }
  }
}
