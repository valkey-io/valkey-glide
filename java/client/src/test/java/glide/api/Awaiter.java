package glide.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Awaiter {
  private static final long DEFAULT_TIMEOUT_MILLISECONDS = 1000;

  /** Get the future result with default timeout. */
  public static <T> T await(CompletableFuture<T> future) {
    return await(future, DEFAULT_TIMEOUT_MILLISECONDS);
  }

  /** Get the future result with given timeout in ms. */
  public static <T> T await(CompletableFuture<T> future, long timeout) {
    try {
      return future.get(timeout, TimeUnit.MILLISECONDS);
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      // TODO: handle exceptions:
      // InterruptedException: should shutdown the client service
      // TimeoutException: should be propagated with an error message thrown
      // ExecutionException: throw runtime exception
      throw new RuntimeException(e);
    }
  }
}
