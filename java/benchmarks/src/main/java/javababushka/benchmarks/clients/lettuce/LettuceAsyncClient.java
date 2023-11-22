package javababushka.benchmarks.clients.lettuce;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.StringCodec;
import java.util.concurrent.Future;
import javababushka.benchmarks.clients.AsyncClient;
import javababushka.benchmarks.utils.ConnectionSettings;

/** A Lettuce client with async capabilities see: https://lettuce.io/ */
public class LettuceAsyncClient implements AsyncClient<String> {

  private RedisClient client;
  private RedisAsyncCommands asyncCommands;
  private StatefulRedisConnection<String, String> connection;

  @Override
  public void connectToRedis() {
    connectToRedis(new ConnectionSettings("localhost", 6379, false, false));
  }

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
    asyncCommands = connection.async();
  }

  @Override
  public Future<String> asyncConnectToRedis(ConnectionSettings connectionSettings) {
    client = RedisClient.create();
    var asyncConnection =
        client.connectAsync(
            new StringCodec(),
            RedisURI.create(
                String.format(
                    "%s://%s:%d",
                    connectionSettings.useSsl ? "rediss" : "redis",
                    connectionSettings.host,
                    connectionSettings.port)));
    asyncConnection.whenComplete((connection, exception) -> asyncCommands = connection.async());
    return asyncConnection.thenApply((connection) -> "OK");
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
