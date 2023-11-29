package babushka.benchmarks.clients.lettuce;

import babushka.benchmarks.clients.AsyncClient;
import babushka.benchmarks.utils.ConnectionSettings;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Duration;

/** A Lettuce client with async capabilities see: https://lettuce.io/ */
public class LettuceAsyncClient implements AsyncClient<String> {
  static final int ASYNC_OPERATION_TIMEOUT_SEC = 1;

  private RedisClient client;
  private RedisAsyncCommands asyncCommands;
  private StatefulRedisConnection<String, String> connection;

  @Override
  public void connectToRedis(ConnectionSettings connectionSettings) {
    client =
        RedisClient.create(
            String.format(
                "%s://%s:%d",
                connectionSettings.useSsl ? "rediss" : "redis",
                connectionSettings.host,
                connectionSettings.port));
    connection = client.connect();
    connection.setTimeout(Duration.ofSeconds(ASYNC_OPERATION_TIMEOUT_SEC));
    asyncCommands = connection.async();
  }

  @Override
  public RedisFuture<String> asyncSet(String key, String value) {
    return asyncCommands.set(key, value);
  }

  @Override
  public RedisFuture<String> asyncGet(String key) {
    return asyncCommands.get(key);
  }

  @Override
  public void closeConnection() {
    connection.close();
    client.shutdown();
  }

  @Override
  public String getName() {
    return "Lettuce Async";
  }
}
