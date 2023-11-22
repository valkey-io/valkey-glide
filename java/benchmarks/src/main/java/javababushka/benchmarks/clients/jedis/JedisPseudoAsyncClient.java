package javababushka.benchmarks.clients.jedis;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import javababushka.benchmarks.clients.AsyncClient;
import javababushka.benchmarks.utils.ConnectionSettings;

/**
 * A Jedis client with pseudo-async capabilities. Jedis doesn't provide async API
 * https://github.com/redis/jedis/issues/241
 *
 * <p>See: https://github.com/redis/jedis
 */
public class JedisPseudoAsyncClient extends JedisClient implements AsyncClient {
  @Override
  public Future<?> asyncConnectToRedis(ConnectionSettings connectionSettings) {
    return CompletableFuture.runAsync(() -> super.connectToRedis(connectionSettings));
  }

  @Override
  public Future<?> asyncSet(String key, String value) {
    return CompletableFuture.runAsync(() -> super.set(key, value));
  }

  @Override
  public Future<String> asyncGet(String key) {
    return CompletableFuture.supplyAsync(() -> super.get(key));
  }

  @Override
  public String getName() {
    return "Jedis pseudo-async";
  }
}
