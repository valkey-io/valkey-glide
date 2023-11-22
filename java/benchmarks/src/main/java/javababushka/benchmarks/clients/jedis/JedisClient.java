package javababushka.benchmarks.clients.jedis;

import javababushka.benchmarks.clients.SyncClient;
import javababushka.benchmarks.utils.ConnectionSettings;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/** A Jedis client with sync capabilities. See: https://github.com/redis/jedis */
public class JedisClient implements SyncClient {

  public static final String DEFAULT_HOST = "localhost";
  public static final int DEFAULT_PORT = 6379;

  protected Jedis jedisResource;

  @Override
  public void connectToRedis() {
    JedisPool pool = new JedisPool(DEFAULT_HOST, DEFAULT_PORT);
    jedisResource = pool.getResource();
  }

  @Override
  public void closeConnection() {
    try {
      jedisResource.close();
    } catch (Exception ignored) {
    }
  }

  @Override
  public String getName() {
    return "Jedis";
  }

  @Override
  public void connectToRedis(ConnectionSettings connectionSettings) {
    jedisResource =
        new Jedis(connectionSettings.host, connectionSettings.port, connectionSettings.useSsl);
    jedisResource.connect();
    if (!jedisResource.isConnected()) {
      throw new RuntimeException("failed to connect to jedis");
    }
  }

  public String info() {
    return jedisResource.info();
  }

  public String info(String section) {
    return jedisResource.info(section);
  }

  @Override
  public void set(String key, String value) {
    jedisResource.set(key, value);
  }

  @Override
  public String get(String key) {
    return jedisResource.get(key);
  }
}
