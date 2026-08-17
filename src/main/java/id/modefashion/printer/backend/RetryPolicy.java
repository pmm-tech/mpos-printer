package id.modefashion.printer.backend;

/** Fixed-interval retry budget for a single ESC/P job. */
public class RetryPolicy {

  private final int maxAttempts;
  private final long backoffMillis;

  public RetryPolicy(int maxAttempts, long backoffMillis) {
    this.maxAttempts = maxAttempts;
    this.backoffMillis = backoffMillis;
  }

  public int maxAttempts() {
    return maxAttempts;
  }

  public long backoffMillis() {
    return backoffMillis;
  }
}
