package babushka.utils;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Awaiter {
  private static final long DEFAULT_TIMEOUT_MILLISECONDS = 30000;

  /** Get the future result with default timeout. */
  public static <T> T await(Future<T> future) {
    return await(future, DEFAULT_TIMEOUT_MILLISECONDS);
  }

  /** Get the future result with given timeout in ms. */
  public static <T> T await(Future<T> future, long timeout) {
    try {
      return future.get(timeout, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new RuntimeException("Request timed out", e);
    } catch (ExecutionException e) {
      throw new RuntimeException(e.getMessage(), e.getCause());
    } catch (InterruptedException e) {
      if (Thread.currentThread().isInterrupted()) {
        // restore interrupt
        Thread.interrupted();
      }
      throw new RuntimeException("The thread was interrupted", e);
    } catch (CancellationException e) {
      throw new RuntimeException("Request was cancelled", e);
    }
  }
}
