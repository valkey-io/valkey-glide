package javababushka.benchmarks;

import java.util.concurrent.Future;

public interface AsyncClient extends Client {

  long DEFAULT_TIMEOUT = 1000; // Milliseconds

  Future<?> asyncSet(String key, String value);

  Future<String> asyncGet(String key);

  <T> T waitForResult(Future<T> future);

  <T> T waitForResult(Future<T> future, long timeout);
}
