package javabushka.client.jedis;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javabushka.client.AsyncClient;

// Jedis doesn't provide async API
// https://github.com/redis/jedis/issues/241
public class JedisPseudoAsyncClient extends JedisClient implements AsyncClient {
  @Override
  public Future<?> asyncSet(String key, String value) {
    return CompletableFuture.runAsync(() -> super.set(key, value));
  }

  @Override
  public Future<String> asyncGet(String key) {
    return CompletableFuture.supplyAsync(() -> super.get(key));
  }

  @Override
  public <T> T waitForResult(Future<T> future) {
    return waitForResult(future, DEFAULT_TIMEOUT);
  }

  @Override
  public <T> T waitForResult(Future<T> future, long timeout) {
    try {
      return future.get(timeout, TimeUnit.MILLISECONDS);
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  public String getName() {
    return "Jedis pseudo-async";
  }
}
